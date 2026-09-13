package com.gregnuovo.hook;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import net.minecraft.server.level.ServerLevel;
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
import com.gregnuovo.core.PatternAnalyzer;
import com.gregnuovo.core.PendingCraft;
import com.gregnuovo.core.RecipeChanceResolver;

/**
 * 所有 mixin 的统一入口（把 AE2 / GTM 内部状态翻译成五个需求的实现）。
 */
public final class GNHooks {

    /** 只打印一次的关键日志，避免刷屏。 */
    private static boolean loggedProviderHook;
    private static boolean loggedBufferHook;
    private static boolean loggedNoTarget;
    private static boolean loggedNoCircuitSlot;

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

        if (!GNConfig.circuitInjection()) return;

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

        CircuitDecision decision = decideCircuit(details, target.workMachine(), true);
        Integer circuit = decision.circuit();
        if (circuit != null) {
            if (MachineAccess.setCircuitOn(target, circuit)) {
                GNDiagnostics.circuitsWritten.incrementAndGet();
                if (GNConfig.debugLog()) {
                    GregNuovo.LOGGER.info("GregNuovo：写入电路 {} -> 收料方块={} 工作机={}（来源={}）", circuit,
                            target.itemHost().getPos(), target.workMachine().getPos(),
                            decision.fromPattern() ? "样板编码" : "配方推断");
                }
                var stripped = PatternAnalyzer.stripCircuits(holder);
                if (!GNConfig.circuitStockless()) {
                    IGrid grid = logic.getGrid();
                    for (GenericStack stack : stripped) {
                        NetworkHelper.insert(grid, src, stack.what(), stack.amount());
                    }
                }
            } else {
                GNDiagnostics.circuitNoSlot.incrementAndGet();
                logNoCircuitSlot(target);
            }
        } else if (decision.ambiguous()) {
            // 候选配方电路不一致：保持机器现状，避免写错导致配方失配
            if (GNConfig.debugLog()) {
                GregNuovo.LOGGER.info("GregNuovo：{} 的候选配方电路不一致，保持机器现有电路", details.getDefinition());
            }
        } else if (GNConfig.clearCircuitWhenAbsent() && MachineAccess.getCircuitOn(target) >= 0) {
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
        if (circuit != null && MachineAccess.getCircuitOn(target) != circuit) {
            // 推料前可能写到了另一台相邻方块上，这里按“真正收到料的那一台”再校准一次
            if (MachineAccess.setCircuitOn(target, circuit)) {
                GNDiagnostics.circuitsWritten.incrementAndGet();
            }
        }

        Set<AEKey> chanced = GNConfig.byproductAware() ? RecipeChanceResolver.chancedOutputs(details, target.workMachine())
                : Set.of();
        Set<AEKey> leftovers = GNConfig.byproductAware()
                ? RecipeChanceResolver.notConsumableInputs(details, target.workMachine())
                : Set.of();

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

        MetaMachine controller = MachineAccess.controllerOf(buffer);
        int index = slotIndexOf(buffer, details);
        Integer circuit = GNConfig.circuitInjection() ? decideCircuit(details, controller, true).circuit() : null;
        boolean slotCircuitReady = false;
        if (index >= 0) {
            // 需求2：样板槽自己的电路（-1 = 无电路）
            GNState.setSlotCircuit(buffer, index, buffer.getInternalInventory().length,
                    circuit == null ? -1 : circuit);
            slotCircuitReady = true;
            if (circuit != null) {
                GNDiagnostics.circuitsWritten.incrementAndGet();
            }
        } else if (GNConfig.debugLog()) {
            GregNuovo.LOGGER.info("GregNuovo：未能定位样板槽下标（{}），本期不接管电路", details.getDefinition());
        }

        // 只有成功接管（该槽位拥有自己的电路）时才剥掉电路物品，否则保持原版行为，避免配方无法匹配
        if (!GNConfig.circuitInjection() || circuit == null || !slotCircuitReady) return;

        var stripped = PatternAnalyzer.stripCircuits(holder);
        if (!GNConfig.circuitStockless()) {
            IGrid grid = buffer.getMainNode() == null ? null : buffer.getMainNode().getGrid();
            for (GenericStack stack : stripped) {
                NetworkHelper.insert(grid, buffer.getActionSource(), stack.what(), stack.amount());
            }
        }
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
                chanced, outputKeys(details), Set.of(), pushed, circuit, index, null, buffer,
                buffer.getActionSource(), null);
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
     *     <li>样板里编码了编程电路 → 用它（需求2 的原始形态）；</li>
     *     <li>样板里没有 → 从该机器可运行的 GT 配方里推断（“配方涉及编程电路”时自动设置）；</li>
     *     <li>推断不出 → 交给调用方按配置清零 / 保持现状。</li>
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
