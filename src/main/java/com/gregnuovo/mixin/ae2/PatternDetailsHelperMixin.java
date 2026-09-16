package com.gregnuovo.mixin.ae2;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gregnuovo.core.ContainerPattern;

/**
 * 需求1：在样板被解码成 {@link IPatternDetails} 的那一刻套上"容器物品"包装。
 *
 * <p>这里是所有样板的唯一入口：AE2 的样板供应器、GTM 的 ME样板总成、以及合成计划器最终拿到的都是
 * 这里返回的对象。因此在这一层包装，规划阶段（{@code CraftingCalculation}）与执行阶段
 * （{@code CraftingCpuLogic}）看到的就是同一个对象，GTM 里以样板为键的 {@code detailsSlotMap}
 * 也能正常命中（包装类的 equals/hashCode 与原样板一致）。</p>
 */
@Mixin(PatternDetailsHelper.class)
public abstract class PatternDetailsHelperMixin {

    @Inject(method = "decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;",
            at = @At("RETURN"), cancellable = true)
    private static void GregNuovo$wrapPattern(ItemStack stack, Level level,
                                              CallbackInfoReturnable<IPatternDetails> cir) {
        cir.setReturnValue(ContainerPattern.wrapIfNeeded(cir.getReturnValue()));
    }

    @Inject(method = "decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Z)Lappeng/api/crafting/IPatternDetails;",
            at = @At("RETURN"), cancellable = true)
    private static void GregNuovo$wrapPatternWithContext(ItemStack stack, Level level, boolean allowMissing,
                                                         CallbackInfoReturnable<IPatternDetails> cir) {
        cir.setReturnValue(ContainerPattern.wrapIfNeeded(cir.getReturnValue()));
    }

    @Inject(method = "decodePattern(Lappeng/api/stacks/AEItemKey;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;",
            at = @At("RETURN"), cancellable = true)
    private static void GregNuovo$wrapPatternByKey(AEItemKey key, Level level,
                                                   CallbackInfoReturnable<IPatternDetails> cir) {
        cir.setReturnValue(ContainerPattern.wrapIfNeeded(cir.getReturnValue()));
    }
}
