package com.gregnuovo.mixin.ae2.accessor;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取合成 CPU 当前任务（AE2 内部字段，需求3/4 的需求判定需要）。 */
@Mixin(CraftingCpuLogic.class)
public interface CraftingCpuLogicAccessor {

    @Accessor("job")
    ExecutingCraftingJob GregNuovo$getJob();
}
