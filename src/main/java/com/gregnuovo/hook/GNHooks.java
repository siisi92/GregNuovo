package com.gregnuovo.hook;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import com.gregnuovo.GregNuovo;
import com.gregnuovo.config.GNConfig;
import com.gregnuovo.core.CraftDemand;
import com.gregnuovo.core.CraftTracker;
import com.gregnuovo.core.GNDiagnostics;
import com.gregnuovo.core.GNState;
import com.gregnuovo.core.MachineAccess;
import com.gregnuovo.core.NetworkHelper;
import com.gregnuovo.core.NonConsumableIndex;
import com.gregnuovo.core.PatternAnalyzer;
import com.gregnuovo.core.PendingCraft;
import com.gregnuovo.core.RecipeChanceResolver;

/**
 * 所有 mixin 的统一入口（把 AE2 / GTM 内部状态翻译成需求的实现）。
 */
public final class GNHooks {

    /** 只打印一次的关键日志，避免刷屏。 */
    private static boolean loggedProviderHook;
    private static boolean loggedBufferHook;
    private static boolean loggedNoTarget;
    private static boolean loggedNoCircuitSlot;
    private static boolean loggedCircuitVerify;
    private static boolean loggedBufferSlotMissing;

    // ------------------------------------------------------------------
    // AE2 样板供应器（单方块机器 / 输入总线 / ME输入总线 等）
    // ------------------------------------------------------------------

    public static void beforeProviderPush(PatternProviderLogic logic, PatternProviderLogicHost host,
                                          IActionSource src, IPatternDetails details, KeyCounter[] holder) {
        GNState.snapshotHolder(holder);
        GNDiagnostics.providerPushes.incrementAndGet();
        if (!loggedProviderHook) {
            loggedProviderHook = true;
            GregNuovo.LOGGER.info("GregNuovo：样板供应器注入已生效（需求1/2 的单方块与总线路径）");
        }

        BlockEntity be = host == null ? null : host.getBlockEntity();
        if (be == null || be.getLevel() == null) return;

        MachineAccess.Target target = MachineAccess.resolveTarget(be.getLevel(), be.getBlockPos(), null);
        if (target == null) {
            GNDiagnostics.targetsMissing.incrementAndGet();
            logNoTarget(be);
            // 相邻不是 GT 机器/零件：完全按原版行为处理
            return;
        }
        GNDiagnostics.targetsResolved.incrementAndGet();

        // 需求1：机器里已经有的不消耗物品不再重复推送，直接把这一份还给 CPU
        withholdPresent(details, holder, target, be.getLevel(), logic.getGrid(), src);

        if (!GNConfig.circuitInjection()) return;

        CircuitDecision decision = decideCircuit(details, target.workMachine(), true);
        Integer circuit = decision.circuit();
        if (circuit != null) {
            if (MachineAccess.setCircuitOn(target, circuit)) {
                GNDiagnostics.circuitsWritten.incrementAndGet();
                markCircuit(target, circuit, be.getLevel());
                verifyCircuit(target, circuit, details);
                if (GNConfig.debugLog()) {
                    GregNuovo.LOGGER.info("GregNuovo：写入电路 {} -> 收料方块={} 工作机={}（来源={}）", circuit,
                            target.itemHost().getPos(), target.workMachine().getPos(),
                            decision.fromPattern() ? "样板编码" : "配方推断");
                }
                var stripped = PatternAnalyzer.stripCircuits(holder);
                returnStrippedCircuits(stripped, logic.getGrid(), src);
            } else {
                GNDiagnostics.circuitNoSlot.incrementAndGet();
                logNoCircuitSlot(target);
            }
        } else if (decision.ambiguous()) {
            // 候选配方电路不一致：保持机器现状，避免写错导致配方失配
            if (GNConfig.debugLog()) {
                GregNuovo.LOGGER.info("GregNuovo：{} 的候选配方电路不一致，保持机器现有电路", details.getDefinition());
            }
        } else if (GNConfig.clearCircuitWhenAbsent() && MachineAccess.getCircuitOn(target) >= 0
                && !GNState.wasCircuitWrittenRecently(target.itemHost(), GNState.now(be.getLevel()))
                && !GNState.wasCircuitWrittenRecently(target.workMachine(), GNState.now(be.getLevel()))) {
            // 需求2：同一台机器上别的样板刚写过电路时不要清零，否则会把刚写好的电路抹掉
            MachineAccess.setCircuitOn(target, -1);
            GNDiagnostics.circuitsCleared.incrementAndGet();
        }
    }

    public static void afterProviderPush(PatternProviderLogic logic, PatternProviderLogicHost host,
                                         IActionSource src, IPatternDetails details, boolean success) {
        Map<AEKey, Long> pushed = GNState.takeHolderSnapshot();
        if (!success || GNState.isRepushing()) return;
        if (!GNConfig.leftoverReturn() && !GNConfig.chancedRetry()) return;

        CraftingCpuLogic cpu = GNState.currentCpu();
        if (cpu == null) return;

        BlockEntity be = host == null ? null : host.getBlockEntity();
        if (be == null || !(be.getLevel() instanceof ServerLevel level)) return;

        pushed.keySet().removeIf(PatternAnalyzer::isCircuitKey);

        MachineAccess.Target target = MachineAccess.resolveTarget(level, be.getBlockPos(), pushed.keySet());
        if (target == null) {
            GNDiagnostics.targetsMissing.incrementAndGet();
            logNoTarget(be);
            return;
        }
        GNDiagnostics.targetsResolved.incrementAndGet();

        MetaMachine itemHost = target.itemHost();
        Integer circuit = GNConfig.circuitInjection()
                ? decideCircuit(details, target.workMachine(), false).circuit()
                : null;
        if (circuit != null) {
            // 推料前可能写到了另一台相邻方块上，这里按“真正收到料的那一台”再校准一次（需求2：强制重写）
            if (MachineAccess.setCircuitOn(target, circuit)) {
                GNDiagnostics.circuitsWritten.incrementAndGet();
                markCircuit(target, circuit, level);
                verifyCircuit(target, circuit, details);
            }
        }

        Set<AEKey> chanced = GNConfig.byproductAware() ? RecipeChanceResolver.chancedOutputs(details, target.workMachine())
                : Set.of();
        Set<AEKey> leftovers = NonConsumableIndex.nonConsumableKeys(details);

        var record = new PendingCraft(PendingCraft.Kind.SINGLE_BLOCK, level.dimension(), be.getBlockPos(), details, cpu,
                chanced, outputKeys(details), leftovers, pushed, circuit, -1, logic, null, src, target.side());
        record.machinePos = target.workMachine().getPos();
        record.itemHostPos = itemHost.getPos();
        CraftTracker.add(record);
        GNDiagnostics.tracked.incrementAndGet();

        if (GNConfig.debugLog()) {
            GregNuovo.LOGGER.info("GregNuovo：登记合成跟踪 目标={} 工作机={} 电路={} 概率产出={}",
                    itemHost.getPos(), target.workMachine().getPos(), circuit, chanced);
        }
    }

    // ------------------------------------------------------------------
    // GTM ME样板总成（多方块）
    // ------------------------------------------------------------------

    public static void beforeBufferPush(MEPatternBufferPartMachine buffer, IPatternDetails details, KeyCounter[] holder) {
        GNState.snapshotHolder(holder);
        GNDiagnostics.bufferPushes.incrementAndGet();
        if (!loggedBufferHook) {
            loggedBufferHook = true;
            GregNuovo.LOGGER.info("GregNuovo：ME样板总成注入已生效（需求1/2 的多方块路径）");
        }

        int index = slotIndexOf(buffer, details);

        // 需求1：槽里已经有的不消耗物品不再重复推送，直接把这一份还给 CPU
        withholdPresentInBuffer(details, holder, buffer, index);

        if (!GNConfig.circuitInjection()) return;

        MetaMachine controller = MachineAccess.controllerOf(buffer);
        Integer circuit = decideCircuit(details, controller, true).circuit();
        boolean slotCircuitReady = false;
        if (index >= 0) {
            // 需求2：样板槽自己的电路（-1 = 无电路）
            GNState.setSlotCircuit(buffer, index, buffer.getInternalInventory().length,
                    circuit == null ? -1 : circuit);
            slotCircuitReady = true;
            if (circuit != null) {
                GNDiagnostics.circuitsWritten.incrementAndGet();
                GNState.markCircuitWritten(slotOf(buffer, index), circuit, GNState.now(buffer.getLevel()));
            }
        } else if (circuit != null) {
            // 定位不到样板槽下标：退而写共用电路槽，至少让这台机器的配方能匹配上
            logBufferSlotMissing(details);
            if (MachineAccess.setCircuit(buffer, circuit)) {
                GNDiagnostics.circuitsWritten.incrementAndGet();
                GNState.markCircuitWritten(buffer, circuit, GNState.now(buffer.getLevel()));
                warnIfMismatch(circuit, MachineAccess.getCircuit(buffer), details);
            } else {
                GNDiagnostics.circuitNoSlot.incrementAndGet();
            }
        } else if (GNConfig.clearCircuitWhenAbsent() && MachineAccess.getCircuit(buffer) >= 0
                && !GNState.wasCircuitWrittenRecently(buffer, GNState.now(buffer.getLevel()))) {
            MachineAccess.setCircuit(buffer, -1);
            GNDiagnostics.circuitsCleared.incrementAndGet();
        }

        // 只有成功接管（该槽位拥有自己的电路）时才剥掉电路物品，否则保持原版行为，避免配方无法匹配
        if (!GNConfig.circuitInjection() || circuit == null || !slotCircuitReady) return;

        IGrid bufferGrid = buffer.getMainNode() == null ? null : buffer.getMainNode().getGrid();
        returnStrippedCircuits(PatternAnalyzer.stripCircuits(holder), bufferGrid, buffer.getActionSource());
    }

    public static void afterBufferPush(MEPatternBufferPartMachine buffer, IPatternDetails details, boolean success) {
        Map<AEKey, Long> pushed = GNState.takeHolderSnapshot();
        if (!success || GNState.isRepushing()) return;
        if (!GNConfig.leftoverReturn() && !GNConfig.chancedRetry()) return;

        CraftingCpuLogic cpu = GNState.currentCpu();
        if (cpu == null) return;
        if (!(buffer.getLevel() instanceof ServerLevel level)) return;

        pushed.keySet().removeIf(PatternAnalyzer::isCircuitKey);

        MetaMachine controller = MachineAccess.controllerOf(buffer);
        Integer circuit = GNConfig.circuitInjection() ? decideCircuit(details, controller, false).circuit() : null;
        Set<AEKey> chanced = GNConfig.byproductAware() && controller != null
                ? RecipeChanceResolver.chancedOutputs(details, controller)
                : Set.of();

        int index = slotIndexOf(buffer, details);
        var record = new PendingCraft(PendingCraft.Kind.MULTIBLOCK, level.dimension(), buffer.getPos(), details, cpu,
                chanced, outputKeys(details), NonConsumableIndex.nonConsumableKeys(details), pushed, circuit, index,
                null, buffer, buffer.getActionSource(), null);
        if (controller != null) {
            record.machinePos = controller.getPos();
        }
        record.itemHostPos = buffer.getPos();
        CraftTracker.add(record);
        GNDiagnostics.tracked.incrementAndGet();
    }

    public static void onBufferPatternChange(MEPatternBufferPartMachine buffer, int index) {
        // 样板被换掉：清掉该槽位的电路
        GNState.setSlotCircuit(buffer, index, buffer.getInternalInventory().length, -1);
    }

    // ------------------------------------------------------------------
    // AE2 合成 CPU
    // ------------------------------------------------------------------

    /**
     * 需求4：把“当前合成任务并不需要”的输出从等待列表中剔除。
     */
    public static void filterExpectedOutputs(@Nullable IPatternDetails details, @Nullable KeyCounter expectedOutputs) {
        if (details == null || expectedOutputs == null || expectedOutputs.size() == 0) return;
        CraftingCpuLogic cpu = GNState.currentCpu();
        if (cpu == null || !cpu.hasJob()) return;

        GenericStack primary = details.getPrimaryOutput();
        for (AEKey key : new ArrayList<>(expectedOutputs.keySet())) {
            if (key == null) continue;
            if (primary != null && key.matches(primary)) continue; // 主产物永远保留
            if (!CraftDemand.isNeeded(cpu, key)) {
                expectedOutputs.remove(key);
                GNDiagnostics.filteredOutputs.incrementAndGet();
            }
        }
    }

    /** 机器完成一次配方。 */
    public static void onRecipeFinished(MetaMachine machine) {
        CraftTracker.onRecipeFinished(machine);
    }

    /**
     * 合成任务即将结束（{@code CraftingCpuLogic#finishJob} 开头）。
     *
     * <p>AE 紧接着会把 CPU 库存整个倒回网络。本模组"凭空构造"的编程电路如果还在里面，
     * 就会变成真物品（等于复制），所以这里先把它清掉——只清我们记过账的那几个键。</p>
     */
    public static void onJobFinishing(CraftingCpuLogic cpu) {
        java.util.Set<AEKey> fabricated = GNState.takeFabricated(cpu);
        if (fabricated.isEmpty()) return;
        try {
            var inventory = cpu.getInventory();
            for (AEKey key : fabricated) {
                long removed = inventory.extract(key, Long.MAX_VALUE, appeng.api.config.Actionable.MODULATE);
                if (removed > 0 && GNConfig.debugLog()) {
                    GregNuovo.LOGGER.info("GregNuovo：任务结束，清除凭空构造的 {} x{}（否则会被倒回网络）",
                            key.getId(), removed);
                }
            }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // 需求1：不消耗物品不重复推送
    // ------------------------------------------------------------------

    /**
     * 机器里已经有了的不消耗物品，从待推送内容里摘掉，并把这一份还给 CPU。
     *
     * <p>AE 每一炉都会按样板取一份不消耗物品出来，但机器根本不会消耗它，
     * 所以要是不管就会在输入槽里越堆越多（这正是"叠加计算"的现场表现）。</p>
     */
    private static void withholdPresent(IPatternDetails details, KeyCounter[] holder, MachineAccess.Target target,
                                        Level level, @Nullable IGrid grid, @Nullable IActionSource src) {
        if (!GNConfig.leftoverReturn() || holder == null) return;
        Set<AEKey> nonConsumable = NonConsumableIndex.nonConsumableKeys(details);
        if (nonConsumable.isEmpty()) return;

        MetaMachine itemHost = target.itemHost();
        MetaMachine work = target.workMachine();
        var withheld = PatternAnalyzer.withhold(holder, key -> !PatternAnalyzer.isCircuitKey(key)
                && contains(nonConsumable, key)
                && (MachineAccess.hasKey(itemHost, level, itemHost.getPos(), target.side(), key, src)
                        || MachineAccess.hasKey(work, level, work.getPos(), target.side(), key, src)));

        returnToCpu(withheld, grid, src);
    }

    private static void withholdPresentInBuffer(IPatternDetails details, KeyCounter[] holder,
                                                MEPatternBufferPartMachine buffer, int index) {
        if (!GNConfig.leftoverReturn() || holder == null) return;
        Set<AEKey> nonConsumable = NonConsumableIndex.nonConsumableKeys(details);
        if (nonConsumable.isEmpty()) return;

        Object slot = index >= 0 ? slotOf(buffer, index) : null;
        var withheld = PatternAnalyzer.withhold(holder, key -> !PatternAnalyzer.isCircuitKey(key)
                && contains(nonConsumable, key) && MachineAccess.slotHasKey(slot, key));

        IGrid grid = buffer.getMainNode() == null ? null : buffer.getMainNode().getGrid();
        returnToCpu(withheld, grid, buffer.getActionSource());
    }

    private static void returnToCpu(java.util.List<GenericStack> withheld, @Nullable IGrid grid,
                                    @Nullable IActionSource src) {
        if (withheld.isEmpty()) return;
        CraftingCpuLogic cpu = GNState.currentCpu();
        for (GenericStack stack : withheld) {
            GNDiagnostics.withheld.incrementAndGet();
            CraftTracker.queueCpuReturn(cpu, grid, src, stack.what(), stack.amount());
            if (GNConfig.debugLog()) {
                GregNuovo.LOGGER.info("GregNuovo：机器里已有 {}，本次不再推送，已交还 CPU", stack.what().getId());
            }
        }
    }

    /**
     * 需求2：把剥下来的编程电路交还给合成 CPU。
     *
     * <p>电路在 AE 眼里是"用完原样还回来"的容器物品（所以"使用物品列表"里只显示 1 份、不消耗），
     * 而它并不会真的进机器——机器用的是我们写进电路槽的编号。所以每一炉剥下来的这一份必须还回
     * 合成 CPU，下一炉才有得拿，否则第二炉就会因为"取不到电路"推不出去。</p>
     */
    private static void returnStrippedCircuits(java.util.List<GenericStack> stripped, @Nullable IGrid grid,
                                               @Nullable IActionSource src) {
        if (stripped.isEmpty()) return;
        CraftingCpuLogic cpu = GNState.currentCpu();
        for (GenericStack stack : stripped) {
            if (GNConfig.circuitStockless()) {
                // 免库存模式下这份电路是凭空构造的：只能还给 CPU，绝不能流进网络（那会复制物品）
                CraftTracker.queueCpuReturnOnly(cpu, stack.what(), stack.amount());
            } else {
                CraftTracker.queueCpuReturn(cpu, grid, src, stack.what(), stack.amount());
            }
        }
    }

    private static boolean contains(Collection<AEKey> keys, AEKey key) {
        for (AEKey candidate : keys) {
            if (RecipeChanceResolver.sameKey(candidate, key)) return true;
        }
        return false;
    }

    @Nullable
    private static Object slotOf(MEPatternBufferPartMachine buffer, int index) {
        try {
            var slots = buffer.getInternalInventory();
            return index >= 0 && index < slots.length ? slots[index] : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 需求2：写完读回校验一次，不一致就记一笔（便于定位"压根没写进去"）。 */
    private static void verifyCircuit(MachineAccess.Target target, int circuit, @Nullable IPatternDetails details) {
        int readBack = Math.max(MachineAccess.getCircuit(target.itemHost()),
                MachineAccess.getCircuit(target.workMachine()));
        warnIfMismatch(circuit, readBack, details);
    }

    /**
     * 记下"这台机器刚被写过电路"。
     *
     * <p>两台都记：{@code setCircuitOn} 会优先写收料方块、写不进去才写工作机，
     * 而 {@code clearWhenAbsent} 要在这两个对象上都能查到"刚写过"才不会误清。</p>
     */
    private static void markCircuit(MachineAccess.Target target, int circuit, @Nullable Level level) {
        long now = GNState.now(level);
        GNState.markCircuitWritten(target.itemHost(), circuit, now);
        if (target.workMachine() != target.itemHost()) {
            GNState.markCircuitWritten(target.workMachine(), circuit, now);
        }
    }

    private static void warnIfMismatch(int wanted, int readBack, @Nullable IPatternDetails details) {
        if (readBack == wanted) return;
        GNDiagnostics.circuitVerifyFailed.incrementAndGet();
        if (loggedCircuitVerify) return;
        loggedCircuitVerify = true;
        GregNuovo.LOGGER.warn("GregNuovo：电路写入后读回不一致（想写 {}，读回 {}，样板 {}）。"
                        + "若反复出现，请把 /gregnuovo status 的输出连同 debugLog=true 的日志发出来。",
                wanted, readBack, details == null ? "?" : details.getDefinition());
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 样板输入里的编程电路编号；没有电路返回 null。 */
    @Nullable
    public static Integer circuitOrNull(IPatternDetails details) {
        if (details == null) return null;
        for (var input : details.getInputs()) {
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible == null) continue;
                int circuit = PatternAnalyzer.circuitOf(possible.what());
                if (circuit >= 0) return circuit;
            }
        }
        return null;
    }

    /** 一次推料的电路决策。 */
    private record CircuitDecision(@Nullable Integer circuit, boolean ambiguous, boolean fromPattern) {}

    /**
     * 决定这次推料给机器写哪个电路：
     * <ol>
     *     <li>样板里编码了编程电路 → 用它（需求2 的原始形态，也是默认唯一来源）；</li>
     *     <li>样板里没有 → 只有显式打开 {@code circuit.autoDetectFromRecipe} 才从 GT 配方推断；</li>
     *     <li>推断不出 → 交给调用方按配置处理。</li>
     * </ol>
     */
    private static CircuitDecision decideCircuit(IPatternDetails details, @Nullable MetaMachine machine, boolean count) {
        Integer fromPattern = circuitOrNull(details);
        if (fromPattern != null) {
            if (count) GNDiagnostics.circuitsFromPattern.incrementAndGet();
            return new CircuitDecision(fromPattern, false, true);
        }
        var hint = GNConfig.circuitAutoDetect() ? RecipeChanceResolver.circuitHint(details, machine)
                : RecipeChanceResolver.CircuitHint.NONE;
        if (hint.circuit() != null) {
            if (count) GNDiagnostics.circuitsAutoDetected.incrementAndGet();
            return new CircuitDecision(hint.circuit(), false, false);
        }
        return new CircuitDecision(null, hint.ambiguous(), false);
    }

    private static void logNoCircuitSlot(MachineAccess.Target target) {
        if (loggedNoCircuitSlot) return;
        loggedNoCircuitSlot = true;
        GregNuovo.LOGGER.warn("GregNuovo：{}（收料方块={}）没有可写电路槽，电路按原版作为物品推送。"
                        + "如果该配方需要电路，请确认这台机器/总线支持电路槽。",
                target.itemHost().getClass().getSimpleName(), target.itemHost().getPos());
    }

    private static void logBufferSlotMissing(IPatternDetails details) {
        if (loggedBufferSlotMissing) return;
        loggedBufferSlotMissing = true;
        GregNuovo.LOGGER.warn("GregNuovo：ME样板总成里定位不到样板对应的槽位（{}），已退回写共用电路槽。",
                details == null ? "?" : details.getDefinition());
    }

    private static void logNoTarget(BlockEntity be) {
        if (loggedNoTarget) return;
        loggedNoTarget = true;
        var level = be.getLevel();
        StringBuilder sb = new StringBuilder();
        for (var dir : net.minecraft.core.Direction.values()) {
            var pos = be.getBlockPos().relative(dir);
            var neighbor = level == null ? null : level.getBlockEntity(pos);
            sb.append(dir).append('=').append(neighbor == null ? "空" : neighbor.getClass().getSimpleName()).append(' ');
        }
        GregNuovo.LOGGER.warn("GregNuovo：{} 周围没有找到 GT 机器/零件，需求1/2 对该供应器不生效。相邻方块：{}",
                be.getBlockPos(), sb);
    }

    private static Set<AEKey> outputKeys(IPatternDetails details) {
        Set<AEKey> keys = new LinkedHashSet<>();
        for (GenericStack stack : PatternAnalyzer.outputs(details)) {
            keys.add(stack.what());
        }
        return keys;
    }

    private static int slotIndexOf(MEPatternBufferPartMachine buffer, IPatternDetails details) {
        var slot = GNState.bufferSlot(buffer, details);
        if (slot == null) return -1;
        var slots = buffer.getInternalInventory();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) return i;
        }
        return -1;
    }

    private GNHooks() {}
}
