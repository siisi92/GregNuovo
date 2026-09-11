package com.gregnuovo.mixin.gtceu;

import com.google.common.collect.BiMap;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import appeng.api.crafting.IPatternDetails;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取 GTM 样板总成内部的“样板 -> 缓存槽”映射（7.3.0 没有公开 getter）。
 */
@Mixin(MEPatternBufferPartMachine.class)
public interface MEPatternBufferAccessor {

    @Accessor("detailsSlotMap")
    BiMap<IPatternDetails, MEPatternBufferPartMachine.InternalSlot> GregNuovo$getDetailsSlotMap();
}
