package com.gregnuovo.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.helpers.patternprovider.PatternProviderLogic;

/**
 * 一次已推送、等待机器完成的合成记录。
 */
public final class PendingCraft {

    public enum Kind {
        /** 单体机器：AE2 样板供应器直接推料。 */
        SINGLE_BLOCK,
        /** 多方块：GTM 样板总成缓冲推料。 */
        MULTIBLOCK
    }

    public enum Phase {
        /** 已推送，等待机器开工。 */
        WAITING_WORK,
        /** 机器正在工作。 */
        WORKING,
        /** 只等机器空闲后退回余料 / 清理电路。 */
        CLEANUP
    }

    public final Kind kind;
    public final ResourceKey<Level> dimension;
    /** 供应器 / 样板总成所在位置。 */
    public final BlockPos partPos;
    /** 实际执行配方的机器（单方块机器本体 / 多方块控制器）；可能稍后解析出来。 */
    @Nullable
    public BlockPos machinePos;
    /** 真正收到物品的机器或零件（输入总线 / ME输入总线 / 样板总成 / 机器本体）：退料与补料都在这里。 */
    @Nullable
    public BlockPos itemHostPos;
    public final IPatternDetails pattern;
    @Nullable
    public final CraftingCpuLogic cpu;
    /** 样板输出中属于 GT 概率产物的键。 */
    public final Set<AEKey> chancedKeys;
    /** 样板全部输出的键（避免把产物当余料取回）。 */
    public final Set<AEKey> outputKeys;
    /** 可以退回的“不消耗物品”键；空集合表示退料时不做筛选。 */
    public final Set<AEKey> leftoverKeys;
    /** 写入机器的电路编号；null 表示样板没有电路。 */
    @Nullable
    public final Integer circuit;
    /** 多方块：样板槽下标；单体：-1。 */
    public final int slotIndex;
    @Nullable
    public final PatternProviderLogic providerLogic;
    @Nullable
    public final MEPatternBufferPartMachine buffer;
    @Nullable
    public final IActionSource actionSource;
    /** 机器朝向供应器的那一面（单体机器用）。 */
    @Nullable
    public final Direction machineSide;

    /** 本次推送但尚未被消耗的内容；重试时会累加。 */
    public final Map<AEKey, Long> pushed = new LinkedHashMap<>();

    public Phase phase = Phase.WAITING_WORK;
    public int ticks;
    public int cooldown;
    public int retries;
    /** 已经收到“配方完成”回调，等待结算。 */
    public boolean cycleFinished;

    public PendingCraft(Kind kind, ResourceKey<Level> dimension, BlockPos partPos, IPatternDetails pattern,
                        @Nullable CraftingCpuLogic cpu, Set<AEKey> chancedKeys, Set<AEKey> outputKeys,
                        Set<AEKey> leftoverKeys, Map<AEKey, Long> pushed, @Nullable Integer circuit, int slotIndex,
                        @Nullable PatternProviderLogic providerLogic, @Nullable MEPatternBufferPartMachine buffer,
                        @Nullable IActionSource actionSource, @Nullable Direction machineSide) {
        this.kind = kind;
        this.dimension = dimension;
        this.partPos = partPos;
        this.pattern = pattern;
        this.cpu = cpu;
        this.chancedKeys = new LinkedHashSet<>(chancedKeys);
        this.outputKeys = new LinkedHashSet<>(outputKeys);
        this.leftoverKeys = new LinkedHashSet<>(leftoverKeys);
        this.circuit = circuit;
        this.slotIndex = slotIndex;
        this.providerLogic = providerLogic;
        this.buffer = buffer;
        this.actionSource = actionSource;
        this.machineSide = machineSide;
        mergePushed(pushed);
    }

    public void mergePushed(Map<AEKey, Long> extra) {
        if (extra == null) return;
        for (var entry : extra.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;
            pushed.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    public String describe() {
        return kind + "@" + partPos.toShortString() + " pattern=" + pattern.getDefinition();
    }
}
