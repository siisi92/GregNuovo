package com.gregnuovo.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;

import com.gregnuovo.GregNuovo;
import com.gregnuovo.config.GNConfig;

/**
 * 合成周期跟踪：
 * <ol>
 *     <li>需求1：每炉结束后，把"AE 正在等的东西"（本炉产物 + 那一份不消耗物品）从机器里收回来，
 *         先交给合成 CPU（它只收自己在等的东西），剩下的进网络；</li>
 *     <li>需求3：当前任务仍需要的概率产物没有产出时，补料重试；</li>
 *     <li>需求4：任务不需要的概率产物，不等待也不重试；</li>
 *     <li>需求2：电路在<b>任务结束</b>后才清（任务期间需要别的电路就直接改写）。</li>
 * </ol>
 *
 * <p>为什么只收"AE 在等的"、而不是把机器清空：GT 机器会缓存 AE 提前推进来的下一炉原料，
 * 那些料 AE 已经记账为消耗掉了，抽走会让机器缺料、任务卡死。</p>
 */
public final class CraftTracker {

    /** 机器完成配方后，等待输出真正落地的宽限时间（tick）。 */
    private static final int SETTLE_GRACE = 5;
    /** 没有收到"配方完成"回调时，判定机器确实空闲的连续空闲 tick 数。 */
    private static final int IDLE_TICKS = 20;
    /** 任务结束后，等待机器停下来的最长时间（tick）。 */
    private static final int CLEANUP_TIMEOUT = 600;

    private static final List<PendingCraft> PENDING = new ArrayList<>();

    /**
     * 需要还给合成 CPU 的东西：推料时发现"机器里已经有了"而摘下来的那一份不消耗物品。
     *
     * <p>必须延后一 tick：AE 是在 {@code pushPattern} 返回<b>之后</b>才把 expectedContainerItems
     * 写进 {@code job.waitingFor} 的，而 {@code CraftingCpuLogic#insert()} 只收自己在等的东西。</p>
     */
    private record CpuReturn(@Nullable CraftingCpuLogic cpu, @Nullable IGrid grid, @Nullable IActionSource src,
                             AEKey key, long amount) {}

    private static final List<CpuReturn> CPU_RETURNS = new ArrayList<>();

    /** 排队把一份东西还给 CPU（CPU 拿不下的部分进网络）。 */
    public static void queueCpuReturn(@Nullable CraftingCpuLogic cpu, @Nullable IGrid grid,
                                      @Nullable IActionSource src, @Nullable AEKey key, long amount) {
        if (key == null || amount <= 0) return;
        synchronized (CPU_RETURNS) {
            CPU_RETURNS.add(new CpuReturn(cpu, grid, src, key, amount));
        }
    }

    private static void flushCpuReturns() {
        List<CpuReturn> pending;
        synchronized (CPU_RETURNS) {
            if (CPU_RETURNS.isEmpty()) return;
            pending = new ArrayList<>(CPU_RETURNS);
            CPU_RETURNS.clear();
        }
        for (CpuReturn entry : pending) {
            long left = entry.amount();
            if (entry.cpu() != null) {
                try {
                    left -= entry.cpu().insert(entry.key(), left, Actionable.MODULATE);
                } catch (Throwable ignored) {}
            }
            if (left > 0) {
                NetworkHelper.insert(entry.grid(), entry.src(), entry.key(), left);
            }
        }
    }

    /** 一次解析出来的目标。 */
    private record Resolved(MetaMachine work, MetaMachine itemHost) {}

    public static void add(PendingCraft craft) {
        synchronized (PENDING) {
            for (PendingCraft existing : PENDING) {
                if (existing.kind == craft.kind && existing.partPos.equals(craft.partPos)
                        && existing.pattern.equals(craft.pattern)) {
                    existing.mergePushed(craft.pushed);
                    if (existing.machinePos == null) existing.machinePos = craft.machinePos;
                    if (existing.itemHostPos == null) existing.itemHostPos = craft.itemHostPos;
                    if (existing.phase == PendingCraft.Phase.CLEANUP) {
                        existing.phase = PendingCraft.Phase.WAITING_WORK;
                    }
                    existing.ticks = 0;
                    existing.cycleFinished = false;
                    return;
                }
            }
            PENDING.add(craft);
        }
    }

    /** 某个机器完成了一次配方（由 RecipeLogic#onRecipeFinish 的 mixin 调用）。 */
    public static void onRecipeFinished(MetaMachine machine) {
        if (machine == null || machine.getLevel() == null) return;
        BlockPos pos = machine.getPos();
        synchronized (PENDING) {
            for (PendingCraft craft : PENDING) {
                if (pos.equals(craft.machinePos) || pos.equals(craft.itemHostPos)) {
                    craft.cycleFinished = true;
                    craft.cooldown = Math.max(craft.cooldown, SETTLE_GRACE);
                    GNDiagnostics.recipesFinished.incrementAndGet();
                }
            }
        }
    }

    public static void clear() {
        synchronized (PENDING) {
            PENDING.clear();
        }
        synchronized (CPU_RETURNS) {
            CPU_RETURNS.clear();
        }
    }

    public static int pendingCount() {
        synchronized (PENDING) {
            return PENDING.size();
        }
    }

    /** 供 /gregnuovo status 使用：列出挂起记录，便于定位"卡在哪一步"。 */
    public static String describePending() {
        List<PendingCraft> snapshot;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return "挂起记录：无";
            snapshot = new ArrayList<>(PENDING);
        }
        StringBuilder sb = new StringBuilder("挂起记录（最多 5 条）：");
        int shown = 0;
        for (PendingCraft craft : snapshot) {
            if (shown++ >= 5) break;
            sb.append('\n').append("  #").append(shown)
                    .append(' ').append(craft.kind)
                    .append(" 阶段=").append(craft.phase)
                    .append(" 计时=").append(craft.ticks)
                    .append(" 收料方块=").append(craft.itemHostPos == null ? "?" : craft.itemHostPos.toShortString())
                    .append(" 工作机=").append(craft.machinePos == null ? "?" : craft.machinePos.toShortString())
                    .append(" 电路=").append(craft.circuit)
                    .append(" 概率产物=").append(craft.chancedKeys.size())
                    .append(" 已推送=").append(craft.pushed.size())
                    .append(" 重试=").append(craft.retries);
        }
        return sb.toString();
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null) return;
        flushCpuReturns();

        List<PendingCraft> snapshot;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            snapshot = new ArrayList<>(PENDING);
        }

        List<PendingCraft> finished = new ArrayList<>();
        for (PendingCraft craft : snapshot) {
            try {
                if (!tick(server, craft)) {
                    finished.add(craft);
                }
            } catch (Throwable t) {
                GregNuovo.LOGGER.warn("GregNuovo：处理合成跟踪记录失败 {}", craft.describe(), t);
                finished.add(craft);
            }
        }
        if (!finished.isEmpty()) {
            synchronized (PENDING) {
                PENDING.removeAll(finished);
            }
        }
    }

    private static boolean tick(MinecraftServer server, PendingCraft craft) {
        ServerLevel level = server.getLevel(craft.dimension);
        if (level == null) return false;

        Resolved resolved = resolve(level, craft);
        if (resolved == null) return false;

        boolean working = MachineAccess.isWorking(resolved.work());
        boolean jobGone = craft.cpu == null || !craft.cpu.hasJob();
        if (craft.cooldown > 0) craft.cooldown--;

        switch (craft.phase) {
            case WAITING_WORK -> {
                if (working) {
                    craft.phase = PendingCraft.Phase.WORKING;
                    craft.ticks = 0;
                    GNDiagnostics.machineStarted.incrementAndGet();
                    return true;
                }
                craft.ticks++;
                if (jobGone && craft.ticks > IDLE_TICKS) {
                    craft.phase = PendingCraft.Phase.CLEANUP;
                    return true;
                }
                // 任务还在进行、机器却一直没开工：继续等待（任务结束时上面的分支会转入清理）
                if (craft.ticks == GNConfig.startTimeoutTicks() + 1 && GNConfig.debugLog()) {
                    GregNuovo.LOGGER.info("GregNuovo：机器迟迟没有开工，继续等待任务结束 {}", craft.describe());
                }
                return true;
            }
            case WORKING -> {
                if (working) {
                    craft.ticks = 0;
                    return true;
                }
                if (craft.cycleFinished && craft.cooldown <= 0) {
                    craft.cycleFinished = false;
                    // 需求1：每炉都先把"AE 在等的东西"收回来（不消耗物品必须回到 CPU，下一炉才有得推）
                    reclaim(level, craft, resolved);
                    if (settle(level, craft, resolved)) {
                        craft.phase = PendingCraft.Phase.WAITING_WORK;
                        craft.retries++;
                        craft.cooldown = GNConfig.retryIntervalTicks();
                        craft.ticks = 0;
                        return true;
                    }
                    if (craft.phase == PendingCraft.Phase.CLEANUP) {
                        craft.ticks = 0;
                        return true;
                    }
                    return false;
                }
                craft.ticks++;
                if (craft.ticks > IDLE_TICKS) {
                    // 没收到"配方完成"回调（例如手动放的配方）：也收一次
                    reclaim(level, craft, resolved);
                    craft.phase = PendingCraft.Phase.CLEANUP;
                }
                return true;
            }
            case CLEANUP -> {
                if (craft.cycleFinished && !craft.chancedKeys.isEmpty() && craft.cooldown <= 0) {
                    craft.cycleFinished = false;
                    craft.phase = PendingCraft.Phase.WORKING;
                    craft.ticks = 0;
                    return true;
                }
                if (!working) {
                    craft.ticks++;
                    if (craft.ticks > IDLE_TICKS) {
                        return finish(level, craft, resolved);
                    }
                } else {
                    craft.ticks = 0;
                }
                if (craft.ticks > CLEANUP_TIMEOUT) return false;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Nullable
    private static Resolved resolve(ServerLevel level, PendingCraft craft) {
        if (craft.kind == PendingCraft.Kind.MULTIBLOCK) {
            MEPatternBufferPartMachine buffer = craft.buffer;
            if (buffer == null) return null;
            if (!level.isLoaded(buffer.getPos())) return null;
            if (MetaMachine.getMachine(level, buffer.getPos()) != buffer) return null;
            MetaMachine controller = MachineAccess.controllerOf(buffer);
            if (controller != null) {
                craft.machinePos = controller.getPos();
                return new Resolved(controller, buffer);
            }
            return new Resolved(buffer, buffer);
        }

        MetaMachine work = machineAt(level, craft.machinePos);
        MetaMachine itemHost = machineAt(level, craft.itemHostPos);
        if (work == null || itemHost == null) {
            var target = MachineAccess.resolveTarget(level, craft.partPos, craft.pushed.keySet());
            if (target == null) return null;
            if (itemHost == null) {
                itemHost = target.itemHost();
                craft.itemHostPos = itemHost.getPos();
            }
            if (work == null) {
                work = target.workMachine();
                craft.machinePos = work.getPos();
            }
        }
        if (work == null) work = itemHost;
        if (itemHost == null) itemHost = work;
        return new Resolved(work, itemHost);
    }

    @Nullable
    private static MetaMachine machineAt(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) return null;
        return MetaMachine.getMachine(level, pos);
    }

    /**
     * 机器刚完成一次配方。
     *
     * @return true 表示已经补料重试（记录继续保留）
     */
    private static boolean settle(ServerLevel level, PendingCraft craft, Resolved resolved) {
        Set<AEKey> stillNeeded = neededChancedKeys(craft);
        if (!stillNeeded.isEmpty() && GNConfig.chancedRetry()) {
            int max = GNConfig.maxChancedRetries();
            if (max <= 0 || craft.retries < max) {
                if (repush(level, craft, resolved, stillNeeded)) {
                    return true;
                }
                GregNuovo.LOGGER.warn("GregNuovo：概率产物补料失败，停止重试（{}）；仍缺少 {}", craft.describe(), stillNeeded);
            } else if (GNConfig.debugLog()) {
                GregNuovo.LOGGER.info("GregNuovo：达到重试上限 {}，停止重试 {}", max, craft.describe());
            }
        }
        // 转入清理阶段：等机器真正空闲后再收尾（避免抢走下一炉的原料）
        craft.phase = PendingCraft.Phase.CLEANUP;
        craft.ticks = 0;
        return false;
    }

    /** 需求3/4：既"还没到手"又"任务确实需要"的概率产物。 */
    private static Set<AEKey> neededChancedKeys(PendingCraft craft) {
        if (craft.chancedKeys.isEmpty()) return Set.of();
        if (craft.cpu == null || !craft.cpu.hasJob()) return Set.of();
        Set<AEKey> needed = new LinkedHashSet<>();
        for (AEKey key : craft.chancedKeys) {
            if (craft.cpu.getWaitingFor(key) <= 0) continue;
            if (!CraftDemand.isNeeded(craft.cpu, key)) continue;
            needed.add(key);
        }
        return needed;
    }

    /**
     * 需求1：把"AE 正在等的东西"从机器里收回，先交给 CPU，剩下的进网络。
     *
     * <p>不消耗物品之所以能一炉一炉循环，靠的就是这里：AE 因为"容器物品"机制会把那一份写进
     * {@code waitingFor}，所以 {@code cpu.insert()} 收得下，下一炉再从 CPU 库存推出去。</p>
     */
    private static void reclaim(ServerLevel level, PendingCraft craft, Resolved resolved) {
        if (!GNConfig.leftoverReturn()) return;

        Set<AEKey> wanted = new LinkedHashSet<>();
        if (craft.cpu != null && craft.cpu.hasJob()) {
            try {
                craft.cpu.getAllWaitingFor(wanted);
            } catch (Throwable ignored) {}
        }
        // 兜底：即使 AE 没在等（例如容器物品包装没生效），识别出来的不消耗物品也要收回来
        if (!craft.leftoverKeys.isEmpty()) {
            for (AEKey key : craft.leftoverKeys) {
                Long pushed = craft.pushed.get(key);
                if (pushed != null && pushed > 0) wanted.add(key);
            }
        }
        if (wanted.isEmpty()) return;

        GNDiagnostics.leftoverScans.incrementAndGet();

        List<GenericStack> got;
        if (craft.kind == PendingCraft.Kind.MULTIBLOCK && craft.buffer != null && craft.slotIndex >= 0) {
            var slots = craft.buffer.getInternalInventory();
            got = craft.slotIndex < slots.length
                    ? MachineAccess.extractKeysFromSlot(slots[craft.slotIndex], wanted)
                    : List.of();
        } else {
            MetaMachine host = resolved.itemHost();
            got = MachineAccess.extractKeys(host, level, host.getPos(), craft.machineSide, wanted, craft.actionSource);
        }
        if (got.isEmpty()) return;

        for (GenericStack stack : got) {
            long left = stack.amount();
            if (craft.cpu != null && craft.cpu.hasJob()) {
                try {
                    left -= craft.cpu.insert(stack.what(), left, Actionable.MODULATE);
                } catch (Throwable ignored) {}
            }
            if (left > 0) {
                NetworkHelper.insert(gridOf(craft), craft.actionSource, stack.what(), left);
            }
            GNDiagnostics.reclaimed.incrementAndGet();
            if (GNConfig.debugLog()) {
                GregNuovo.LOGGER.info("GregNuovo：收回 {} x{}（{}）", stack.what().getId(), stack.amount(),
                        craft.describe());
            }
        }
    }

    /**
     * 任务结束后的收尾。
     *
     * @return true 表示任务还在跑，记录继续保留（电路要留到任务结束才清）
     */
    private static boolean finish(ServerLevel level, PendingCraft craft, Resolved resolved) {
        GNDiagnostics.cleanupRuns.incrementAndGet();
        try {
            reclaim(level, craft, resolved);
        } catch (Throwable t) {
            GregNuovo.LOGGER.warn("GregNuovo：收回残留失败 {}", craft.describe(), t);
        }

        boolean jobAlive = craft.cpu != null && craft.cpu.hasJob();
        if (jobAlive) {
            // 需求2：电路留到任务结束才清；任务期间需要别的电路会直接改写
            craft.phase = PendingCraft.Phase.WAITING_WORK;
            craft.ticks = 0;
            return true;
        }

        try {
            if (GNConfig.clearCircuitAfterCraft() && craft.circuit != null) {
                MachineAccess.setCircuit(resolved.itemHost(), -1);
                if (resolved.work() != resolved.itemHost()) {
                    MachineAccess.setCircuit(resolved.work(), -1);
                }
                GNDiagnostics.circuitsCleared.incrementAndGet();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** 需求3：补料重试。 */
    private static boolean repush(ServerLevel level, PendingCraft craft, Resolved resolved, Set<AEKey> stillNeeded) {
        IGrid grid = gridOf(craft);
        if (grid == null) return false;

        KeyCounterBundle bundle = extractPatternInputsForRepush(craft, grid);
        if (bundle == null) return false;

        if (craft.kind == PendingCraft.Kind.MULTIBLOCK) {
            MEPatternBufferPartMachine buffer = craft.buffer;
            if (buffer == null) return false;
            if (craft.circuit != null && craft.slotIndex >= 0) {
                GNState.setSlotCircuit(buffer, craft.slotIndex, buffer.getInternalInventory().length, craft.circuit);
            }
            GNState.setRepushing(true);
            boolean pushed;
            try {
                pushed = buffer.pushPattern(craft.pattern, bundle.holder());
            } finally {
                GNState.setRepushing(false);
            }
            if (!pushed) {
                rollback(grid, craft, bundle.extracted(), bundle.extractedFromCpu());
                return false;
            }
        } else {
            MetaMachine host = resolved.itemHost();
            if (craft.circuit != null) {
                MachineAccess.setCircuit(host, craft.circuit);
            } else if (GNConfig.clearCircuitWhenAbsent()) {
                MachineAccess.setCircuit(host, -1);
            }
            for (ItemStack stack : bundle.stacks()) {
                if (!MachineAccess.insertItem(host, craft.machineSide, stack)) {
                    AEItemKey key = AEItemKey.of(stack);
                    if (key != null) {
                        NetworkHelper.insert(grid, craft.actionSource, key, stack.getCount());
                    }
                }
            }
        }

        craft.mergePushed(bundle.extracted());
        craft.phase = PendingCraft.Phase.WAITING_WORK;
        craft.ticks = 0;
        craft.cycleFinished = false;
        GNDiagnostics.retries.incrementAndGet();
        if (GNConfig.debugLog()) {
            GregNuovo.LOGGER.info("GregNuovo：为概率产物补料重试（第 {} 次），缺少 {}", craft.retries + 1, stillNeeded);
        }
        return true;
    }

    private record KeyCounterBundle(appeng.api.stacks.KeyCounter[] holder, Map<AEKey, Long> extracted,
                                    Map<AEKey, Long> extractedFromCpu, List<ItemStack> stacks) {}

    /**
     * 按样板输入取料：<b>先取 CPU 内部库存，不够再取网络</b>。
     *
     * <p>先取 CPU 是需求1 的关键：不消耗物品在合成期间就待在 CPU 库存里，
     * 只在网络里找是找不到的。取不齐则回滚并返回 null。</p>
     */
    @Nullable
    private static KeyCounterBundle extractPatternInputsForRepush(PendingCraft craft, IGrid grid) {
        var holder = PatternAnalyzer.expandInputs(craft.pattern);
        PatternAnalyzer.stripCircuits(holder);

        Map<AEKey, Long> extracted = new LinkedHashMap<>();
        Map<AEKey, Long> fromCpu = new LinkedHashMap<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (var counter : holder) {
            for (var entry : counter) {
                AEKey key = entry.getKey();
                long want = entry.getLongValue();
                long gotCpu = extractFromCpu(craft, key, want);
                long gotNet = gotCpu >= want ? 0
                        : NetworkHelper.extract(grid, craft.actionSource, key, want - gotCpu);
                long got = gotCpu + gotNet;
                if (got < want) {
                    if (gotCpu > 0) insertToCpu(craft, key, gotCpu);
                    if (gotNet > 0) NetworkHelper.insert(grid, craft.actionSource, key, gotNet);
                    rollback(grid, craft, extracted, fromCpu);
                    return null;
                }
                extracted.merge(key, got, Long::sum);
                if (gotCpu > 0) fromCpu.merge(key, gotCpu, Long::sum);
                if (key instanceof AEItemKey itemKey) {
                    long left = got;
                    int maxStack = Math.max(1, itemKey.toStack().getMaxStackSize());
                    while (left > 0) {
                        int size = (int) Math.min(left, maxStack);
                        stacks.add(itemKey.toStack(size));
                        left -= size;
                    }
                }
            }
        }
        if (extracted.isEmpty()) return null;
        return new KeyCounterBundle(holder, extracted, fromCpu, stacks);
    }

    private static long extractFromCpu(PendingCraft craft, AEKey key, long want) {
        if (want <= 0 || craft.cpu == null || !craft.cpu.hasJob()) return 0;
        try {
            return craft.cpu.getInventory().extract(key, want, Actionable.MODULATE);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void insertToCpu(PendingCraft craft, AEKey key, long amount) {
        if (amount <= 0 || craft.cpu == null || !craft.cpu.hasJob()) return;
        try {
            craft.cpu.getInventory().insert(key, amount, Actionable.MODULATE);
        } catch (Throwable ignored) {}
    }

    private static void rollback(IGrid grid, PendingCraft craft, Map<AEKey, Long> extracted,
                                 Map<AEKey, Long> fromCpu) {
        for (var entry : extracted.entrySet()) {
            long cpuPart = fromCpu.getOrDefault(entry.getKey(), 0L);
            if (cpuPart > 0) insertToCpu(craft, entry.getKey(), cpuPart);
            long netPart = entry.getValue() - cpuPart;
            if (netPart > 0) NetworkHelper.insert(grid, craft.actionSource, entry.getKey(), netPart);
        }
    }

    @Nullable
    private static IGrid gridOf(PendingCraft craft) {
        if (craft.kind == PendingCraft.Kind.MULTIBLOCK && craft.buffer != null) {
            return craft.buffer.getMainNode().getGrid();
        }
        if (craft.providerLogic != null) {
            return craft.providerLogic.getGrid();
        }
        return null;
    }

    private CraftTracker() {}
}
