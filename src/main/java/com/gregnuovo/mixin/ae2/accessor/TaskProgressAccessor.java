package com.gregnuovo.mixin.ae2.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取某个样板任务还剩多少次未推送（包私有内部类，用 targets 字符串定位）。 */
@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress")
public interface TaskProgressAccessor {

    @Accessor("value")
    long GregNuovo$getValue();
}
