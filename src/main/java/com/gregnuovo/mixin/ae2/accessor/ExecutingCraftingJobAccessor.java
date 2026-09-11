package com.gregnuovo.mixin.ae2.accessor;

import java.util.Map;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.ExecutingCraftingJob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取当前合成任务的最终产物、剩余数量与未完成样板任务。 */
@Mixin(ExecutingCraftingJob.class)
public interface ExecutingCraftingJobAccessor {

    @Accessor("finalOutput")
    GenericStack GregNuovo$getFinalOutput();

    @Accessor("remainingAmount")
    long GregNuovo$getRemainingAmount();

    @Accessor("tasks")
    Map<IPatternDetails, ?> GregNuovo$getTasks();
}
