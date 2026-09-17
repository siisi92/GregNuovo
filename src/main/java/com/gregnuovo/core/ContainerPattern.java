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

        // 需求2：编程电路不是"输入物品"，它只是写在样板里、告诉机器该用哪个电路的一条指令。
        // 所以这里直接把它从输入里摘掉：AE 既不会把它算进需求量（"使用物品列表"里不再出现），
        // 也不会去网络/CPU 里取它、更不会推进机器——机器那边由本模组写电路槽。
        IInput[] original = delegate.getInputs();
        java.util.List<IInput> kept = new java.util.ArrayList<>(original.length);
        for (IInput input : original) {
            if (isCircuitInput(input)) continue;
            kept.add(new ContainerInput(input));
        }
        this.inputs = kept.toArray(new IInput[0]);
    }

    /** 这个输入是不是编程电路（候选项里有电路就算）。 */
    private static boolean isCircuitInput(@Nullable IInput input) {
        if (input == null) return false;
        try {
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible != null && PatternAnalyzer.isCircuitKey(possible.what())) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * 需要时给样板套一层包装（摘掉电路输入 + 把不消耗输入当成"用完还回来"）；
     * 两者都不涉及就原样返回。
     */
    @Nullable
    public static IPatternDetails wrapIfNeeded(@Nullable IPatternDetails details) {
        if (details == null) return null;
        // 已经包装过就不重复套
        if (details instanceof ContainerPattern) return details;
        // 只处理加工样板：合成/切石/锻造样板本身就有容器物品语义，别去动它们
        if (!(details instanceof appeng.crafting.pattern.AEProcessingPattern)) return details;
        try {
            // 只要有电路输入就必须包装（哪怕一个不消耗输入都没有）
            boolean hasCircuit = PatternAnalyzer.hasCircuitInput(details);
            Set<AEKey> nonConsumable = NonConsumableIndex.nonConsumableKeys(details);
            if (!hasCircuit && nonConsumable.isEmpty()) return details;
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

    /**
     * 取回原始样板（本模组包装过的话）。
     *
     * <p>读"样板里写的是哪个电路"必须走这里：包装后的 {@link #getInputs()} 已经把电路摘掉了，
     * 直接读会读到"没有电路"。</p>
     */
    public static IPatternDetails raw(@Nullable IPatternDetails details) {
        return details instanceof ContainerPattern wrapper ? wrapper.delegate : details;
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
        // 不能直接委托给原样板：它的实现会按"原始输入"（含电路）去核对数量，
        // 而我们这里已经把电路摘掉了，委托过去会报"Expected at least 1 of <电路>"。
        // 这里按 AE 的默认语义（把 holder 里的东西原样推给 sink）自己推一遍即可。
        if (inputHolder == null) return;
        for (var counter : inputHolder) {
            if (counter == null) continue;
            for (var entry : counter) {
                inputSink.pushInput(entry.getKey(), entry.getLongValue());
            }
        }
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
