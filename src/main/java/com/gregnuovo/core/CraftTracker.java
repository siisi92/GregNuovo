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
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.gregnuovo.GregNuovo;
import com.gregnuovo.config.GNConfig;

/**
 * 合成周期跟踪：
 * <ol>
 *     <li>需求1：机器空闲后，把“本次推送但未被消耗”的余料退回 AE；</li>
 *     <li>需求3：当前任务仍需要的概率产物没有产出时，补料重试；</li>
 *     <li>需求4：任务不需要的概率产物，不等待也不重试。</li>
 * </ol>
 */
public final class CraftTracker {

    /** 机器完成配方后，等待输出真正落地的宽限时间（tick）。 */
    private static final int SETTLE_GRACE = 5;
    /** 判定“机器确实空闲”的连续空闲 tick 数。 */
    private static final int IDLE_TICKS = 20;
    /** 任务结束后，等待机器停下来的最长时间（tick）。 */
    private static final int CLEANUP_TIMEOUT = 600;

    private static final List<PendingCraft> PENDING = new ArrayList<>();

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
    }

    public static int pendingCount() {
        synchronized (PENDING) {
            return PENDING.size();
        }
    }

    /** 供 /GregNuovo status 使用：列出挂起记录，便于定位“卡在哪一步”。 */
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
        List<PendingCraft> snapshot;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            snapshot = new ArrayList<>(PENDING);
        }
        if (server == null) return;

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
                // 任务还在进行、机器却一直没开工：继续等待（任务结束时上面的分支会转入清理并退回余料）
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
                        cleanup(level, craft, resolved);
                        return false;
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
        // 转入清理阶段：等机器真正空闲后再退料（避免抢走下一炉的原料）
        craft.phase = PendingCraft.Phase.CLEANUP;
        craft.ticks = 0;
        return false;
    }

    /** 需求3/4：既“还没到手”又“任务确实需要”的概率产物。 */
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

    /** 需求1：退回余料 + 清理电路。 */
    private static void cleanup(ServerLevel level, PendingCraft craft, Resolved resolved) {
        GNDiagnostics.cleanupRuns.incrementAndGet();
        try {
            returnLeftovers(level, craft, resolved);
        } catch (Throwable t) {
            GregNuovo.LOGGER.warn("GregNuovo：退回余料失败 {}", craft.describe(), t);
        }
        try {
            if (GNConfig.clearCircuitAfterCraft() && craft.circuit != null) {
                MachineAccess.setCircuit(resolved.itemHost(), -1);
                if (resolved.work() != resolved.itemHost()) {
                    MachineAccess.setCircuit(resolved.work(), -1);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void returnLeftovers(ServerLevel level, PendingCraft craft, Resolved resolved) {
        if (!GNConfig.leftoverReturn()) return;

        if (craft.kind == PendingCraft.Kind.MULTIBLOCK) {
            MEPatternBufferPartMachine buffer = craft.buffer;
            if (buffer == null || craft.slotIndex < 0) return;
            var slots = buffer.getInternalInventory();
            if (craft.slotIndex >= slots.length) return;
            var slot = slots[craft.slotIndex];
            if (slot != null && (!slot.isItemEmpty() || !slot.isFluidEmpty())) {
                slot.refund();
                GNDiagnostics.leftoversReturned.incrementAndGet();
                if (GNConfig.debugLog()) {
                    GregNuovo.LOGGER.info("GregNuovo：样板总成余料已退回网络 {}", craft.describe());
                }
            }
            return;
        }

        GNDiagnostics.leftoverScans.incrementAndGet();
        MetaMachine host = resolved.itemHost();
        List<GenericStack> leftovers = MachineAccess.extractLeftovers(host, level,
                host.getPos(), craft.machineSide, craft.pushed, craft.outputKeys, craft.leftoverKeys,
                craft.cpu == null || !craft.cpu.hasJob(), craft.actionSource);

        if (leftovers.isEmpty()) {
            if (GNConfig.debugLog()) {
                GregNuovo.LOGGER.info("GregNuovo：退料扫描未发现余料（{}），本次推送={}，不消耗键={}，收料方块={}",
                        craft.describe(), craft.pushed.keySet().size(), craft.leftoverKeys.size(),
                        host.getClass().getSimpleName());
            }
            return;
        }

        IGrid grid = gridOf(craft);
        for (GenericStack stack : leftovers) {
            AEKey key = stack.what();
            long inserted = NetworkHelper.insert(grid, craft.actionSource, key, stack.amount());
            long remaining = stack.amount() - inserted;
            if (remaining > 0 && craft.providerLogic != null) {
                craft.providerLogic.getReturnInv().insert(key, remaining,
                        appeng.api.config.Actionable.MODULATE, craft.actionSource);
            }
            GNDiagnostics.leftoversReturned.incrementAndGet();
            if (GNConfig.debugLog()) {
                GregNuovo.LOGGER.info("GregNuovo：退回余料 {} x{}", key.getId(), stack.amount());
            }
        }
    }

    /** 需求3：补料重试。 */
    private static boolean repush(ServerLevel level, PendingCraft craft, Resolved resolved, Set<AEKey> stillNeeded) {
        IGrid grid = gridOf(craft);
        if (grid == null) return false;

        KeyCounterBundle bundle = extractPatternInputs(craft, grid);
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
                rollback(grid, craft, bundle.extracted());
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
                                    List<ItemStack> stacks) {}

    /** 按样板输入从网络取料，取不齐则回滚并返回 null。 */
    @Nullable
    private static KeyCounterBundle extractPatternInputs(PendingCraft craft, IGrid grid) {
        var holder = PatternAnalyzer.expandInputs(craft.pattern);
        PatternAnalyzer.stripCircuits(holder);

        Map<AEKey, Long> extracted = new LinkedHashMap<>();
        List<ItemStack> stacks = new ArrayList<>();
        outer:
        for (var counter : holder) {
            for (var entry : counter) {
                AEKey key = entry.getKey();
                long want = entry.getLongValue();
                long got = NetworkHelper.extract(grid, craft.actionSource, key, want);
                if (got < want) {
                    if (got > 0) {
                        NetworkHelper.insert(grid, craft.actionSource, key, got);
                    }
                    rollback(grid, craft, extracted);
                    return null;
                }
                extracted.merge(key, got, Long::sum);
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
        return new KeyCounterBundle(holder, extracted, stacks);
    }

    private static void rollback(IGrid grid, PendingCraft craft, Map<AEKey, Long> extracted) {
        for (var entry : extracted.entrySet()) {
            NetworkHelper.insert(grid, craft.actionSource, entry.getKey(), entry.getValue());
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
