package com.gregnuovo.core;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;

import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

/**
 * 样板内容的解析工具。
 */
public final class PatternAnalyzer {

    /** 判断一个 AE 键是否是 GT 编程电路。 */
    public static boolean isCircuitKey(@Nullable AEKey key) {
        return key instanceof AEItemKey itemKey && isCircuitStack(itemKey.toStack());
    }

    public static boolean isCircuitStack(ItemStack stack) {
        return !stack.isEmpty() && IntCircuitBehaviour.isIntegratedCircuit(stack);
    }

    /** 读取编程电路的编号；不是电路时返回 -1。 */
    public static int circuitOf(@Nullable AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            var stack = itemKey.toStack();
            if (isCircuitStack(stack)) {
                return IntCircuitBehaviour.getCircuitConfiguration(stack);
            }
        }
        return -1;
    }

    /**
     * 从待推送的输入中剥离编程电路。
     *
     * @return 被剥离的 (电路编号,数量) 列表；同时把电路从 holder 中移除。
     */
    public static List<GenericStack> stripCircuits(KeyCounter[] holder) {
        List<GenericStack> stripped = new ArrayList<>();
        if (holder == null) return stripped;
        for (var counter : holder) {
            if (counter == null) continue;
            for (AEKey key : new ArrayList<>(counter.keySet())) {
                if (!isCircuitKey(key)) continue;
                long amount = counter.remove(key);
                if (amount > 0) {
                    stripped.add(new GenericStack(key, amount));
                }
            }
        }
        return stripped;
    }

    /** 样板输入中是否含有编程电路。 */
    public static boolean hasCircuitInput(IPatternDetails details) {
        for (var input : details.getInputs()) {
            for (var possible : input.getPossibleInputs()) {
                if (isCircuitKey(possible.what())) return true;
            }
        }
        return false;
    }

    /**
     * 把样板输入展开为“每个输入槽一个 KeyCounter”的结构，用于重试时自行推送。
     * 每个输入取第一个可用候选（与 AE 样板编码一致）。
     */
    public static KeyCounter[] expandInputs(IPatternDetails details) {
        var inputs = details.getInputs();
        var holder = new KeyCounter[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            var counter = new KeyCounter();
            holder[i] = counter;
            var possible = inputs[i].getPossibleInputs();
            if (possible.length == 0) continue;
            var first = possible[0];
            if (first == null || first.what() == null) continue;
            long amount = first.amount() * Math.max(1L, inputs[i].getMultiplier());
            counter.add(first.what(), amount);
        }
        return holder;
    }

    /** 样板的全部输出。 */
    public static List<GenericStack> outputs(IPatternDetails details) {
        // 1.21.1（AE2 19.x）起 getOutputs() 返回 List<GenericStack>（15.x 是数组）
        var outs = details.getOutputs();
        List<GenericStack> list = new ArrayList<>(outs.size());
        for (var out : outs) {
            if (out != null && out.what() != null && out.amount() > 0) {
                list.add(out);
            }
        }
        return list;
    }

    /** 样板输入里出现的所有物品/流体键（不含电路）。 */
    public static List<AEKey> inputKeys(IPatternDetails details) {
        List<AEKey> keys = new ArrayList<>();
        for (var input : details.getInputs()) {
            for (var possible : input.getPossibleInputs()) {
                if (possible == null || possible.what() == null) continue;
                if (isCircuitKey(possible.what())) continue;
                if (!keys.contains(possible.what())) {
                    keys.add(possible.what());
                }
            }
        }
        return keys;
    }

    private PatternAnalyzer() {}
}
