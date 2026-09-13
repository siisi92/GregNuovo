package com.gregnuovo.mixin.gtceu;

import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gregnuovo.hook.GNHooks;

/**
 * 多方块一侧：在 GTM ME样板总成推料前后介入（需求1/2/3/4）。
 */
@Mixin(MEPatternBufferPartMachine.class)
public abstract class MEPatternBufferMixin {

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void GregNuovo$beforePush(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                 CallbackInfoReturnable<Boolean> cir) {
        GNHooks.beforeBufferPush((MEPatternBufferPartMachine) (Object) this, patternDetails, inputHolder);
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void GregNuovo$afterPush(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                CallbackInfoReturnable<Boolean> cir) {
        GNHooks.afterBufferPush((MEPatternBufferPartMachine) (Object) this, patternDetails, cir.getReturnValueZ());
    }

    @Inject(method = "onPatternChange", at = @At("HEAD"))
    private void GregNuovo$onPatternChange(int index, CallbackInfo ci) {
        GNHooks.onBufferPatternChange((MEPatternBufferPartMachine) (Object) this, index);
    }
}
