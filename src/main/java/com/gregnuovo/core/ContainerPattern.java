package com.gregnuovo.core;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

/**
 * 需求1 的核心实现：把 GT 的"不消耗输入"伪装成 AE 的<b>容器物品</b>。
 *
 * <p>AE 判断"这个输入用完会还回来"靠的是 {@link IInput#getRemainingKey(AEKey)}（空桶就是这么实现的）。
 * 一旦某个输入返回了非 null，AE 会：</p>
 * <ol>
 *     <li>{@code CraftingTreeProcess#updateLimitQty()}：把该样板标记成 containerItems + limitQty，每次只模拟一炉；</li>
 *     <li>{@code CraftingTreeNode#request()}：模拟时把那一份<b>加回模拟库存</b>；</li>
 *     <li>而 {@code CraftingSimulationState} 记的需求量是"峰值缺口"，不是累计消耗。</li>
 * </ol>
 * <p>于是整个合成任务对它的需求永远是 <b>1 份</b>，下单 64 个也不会被算成 64 份。</p>
 *
 * <p>执行侧同样现成：{@code CraftingCpuHelper#extractPatternInputs()} 会把它填进 expectedContainerItems，
 * {@code CraftingCpuLogic#executeCrafting()} 再写进 {@code job.waitingFor}，
 * 所以那一份可以被 {@code CraftingCpuLogic#insert()} 收下（CPU 只收自己在等的东西）。</p>
 *
 * <p>这个包装必须与原样板 {@code equals}/{@code hashCode} 一致，否则 GTM 样板总成里
 * 以样板为键的 {@code detailsSlotMap} 会找不到槽位。</p>
 */
public final class ContainerPattern implements IPatternDetails {

    private final IPatternDetails delegate;
    private final IInput[] inputs;
    private final Set<AEKey> nonConsumable;

    private ContainerPattern(IPatternDetails delegate, Set<AEKey> nonConsumable) {
        this.delegate = delegate;
        this.nonConsumable = nonConsumable;

        IInput[] original = delegate.getInputs();
        this.inputs = new IInput[original.length];
        for (int i = 0; i < original.length; i++) {
            this.inputs[i] = new ContainerInput(original[i]);
        }
    }

    /**
     * 需要时给样板套上"容器物品"包装；没有不消耗输入时原样返回。
     */
    @Nullable
    public static IPatternDetails wrapIfNeeded(@Nullable IPatternDetails details) {
        if (details == null) return null;
        // 已经包装过就不重复套
        if (details instanceof ContainerPattern) return details;
        // 只处理加工样板：合成/切石/锻造样板本身就有容器物品语义，别去动它们
        if (!(details instanceof appeng.crafting.pattern.AEProcessingPattern)) return details;
        try {
            Set<AEKey> nonConsumable = NonConsumableIndex.nonConsumableKeys(details);
            if (nonConsumable.isEmpty()) return details;
            GNDiagnostics.containerPatterns.incrementAndGet();
            return new ContainerPattern(details, nonConsumable);
        } catch (Throwable t) {
            return details;
        }
    }

    /** 这个键在这个样板里是不是"用完原样还回来"。 */
    private boolean returns(AEKey key) {
        if (key == null) return false;
        for (AEKey candidate : nonConsumable) {
            if (RecipeChanceResolver.sameKey(candidate, key)) return true;
        }
        return false;
    }

    @Override
    public AEItemKey getDefinition() {
        return delegate.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public GenericStack[] getOutputs() {
        return delegate.getOutputs();
    }

    @Override
    public GenericStack getPrimaryOutput() {
        return delegate.getPrimaryOutput();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        delegate.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ContainerPattern other) {
            return delegate.equals(other.delegate);
        }
        return delegate.equals(obj);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return "ContainerPattern[" + delegate + ", nonConsumable=" + nonConsumable.size() + "]";
    }

    /** 把某个输入的"剩余物"改成它自己。 */
    private final class ContainerInput implements IInput {

        private final IInput delegate;

        private ContainerInput(IInput delegate) {
            this.delegate = delegate;
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return delegate.getPossibleInputs();
        }

        @Override
        public long getMultiplier() {
            return delegate.getMultiplier();
        }

        @Override
        public boolean isValid(AEKey key, Level level) {
            return delegate.isValid(key, level);
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            if (returns(template)) {
                // 用完原样还回来 —— AE 由此认为这份材料不会被消耗
                return template;
            }
            return delegate.getRemainingKey(template);
        }
    }
}
