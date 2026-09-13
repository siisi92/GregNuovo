package com.gregnuovo.core;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;

import com.gregnuovo.mixin.ae2.accessor.CraftingCpuLogicAccessor;
import com.gregnuovo.mixin.ae2.accessor.ExecutingCraftingJobAccessor;
import com.gregnuovo.mixin.ae2.accessor.TaskProgressAccessor;

/**
 * 需求4 的判定基础：当前 AE 合成任务到底“需不需要”某个键。
 *
 * <p>判定依据来自合成 CPU 内部状态：</p>
 * <ul>
 *     <li>最终产物尚未满足的数量；</li>
 *     <li>尚未推送完成的样板任务（{@code job.tasks}）中，把该键当作输入的需求量；</li>
 * </ul>
 * <p>再减去 CPU 内部已暂存的量。</p>
 */
public final class CraftDemand {

    /** 当前任务是否需要该键。 */
    public static boolean isNeeded(@Nullable CraftingCpuLogic cpu, AEKey key) {
        if (cpu == null) return false;
        ExecutingCraftingJob job;
        try {
            job = ((CraftingCpuLogicAccessor) cpu).GregNuovo$getJob();
        } catch (Throwable t) {
            return true; // 拿不到内部状态时保守处理：按“需要”处理
        }
        if (job == null) return false;

        long demand = 0;
        try {
            var accessor = (ExecutingCraftingJobAccessor) job;
            GenericStack finalOutput = accessor.GregNuovo$getFinalOutput();
            if (finalOutput != null && finalOutput.what() != null && key.matches(finalOutput)) {
                // 最终产物：只要还有没交付的量就视为“需要”，
                // 绝不能因为 CPU 内部恰好暂存了同名物品就判定为不需要（否则请求方永远收不到货）
                if (accessor.GregNuovo$getRemainingAmount() > 0) return true;
            }
            Map<IPatternDetails, ?> tasks = accessor.GregNuovo$getTasks();
            if (tasks != null) {
                for (var entry : tasks.entrySet()) {
                    long crafts = taskValue(entry.getValue());
                    if (crafts <= 0) continue;
                    demand += inputAmount(entry.getKey(), key) * crafts;
                }
            }
        } catch (Throwable t) {
            return true;
        }

        if (demand <= 0) return false;
        try {
            return demand > cpu.getStored(key);
        } catch (Throwable t) {
            return true;
        }
    }

    private static long taskValue(@Nullable Object task) {
        if (task == null) return 0;
        try {
            return ((TaskProgressAccessor) task).GregNuovo$getValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 样板每做一次，需要多少该键作为输入。 */
    private static long inputAmount(@Nullable IPatternDetails details, AEKey key) {
        if (details == null) return 0;
        long sum = 0;
        for (var input : details.getInputs()) {
            long multiplier = Math.max(1L, input.getMultiplier());
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible == null || possible.what() == null) continue;
                if (key.matches(possible)) {
                    sum += possible.amount() * multiplier;
                    break;
                }
            }
        }
        return sum;
    }

    private CraftDemand() {}
}
