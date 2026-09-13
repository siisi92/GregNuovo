package com.gregnuovo.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gregnuovo.hook.GNHooks;

/**
 * 单体机器一侧：在 AE2 原版样板供应器推料前后介入。
 * <ul>
 *     <li>需求2：把样板里的编程电路写进相邻 GT 机器的电路槽（不作为物品推送）；</li>
 *     <li>需求1/3：登记本次推送，用于合成结束后退料与概率产物补料。</li>
 * </ul>
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin {

    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Shadow
    @Final
    private IActionSource actionSource;

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void GregNuovo$beforePush(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                 CallbackInfoReturnable<Boolean> cir) {
        GNHooks.beforeProviderPush((PatternProviderLogic) (Object) this, host, actionSource, patternDetails,
                inputHolder);
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void GregNuovo$afterPush(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                CallbackInfoReturnable<Boolean> cir) {
        GNHooks.afterProviderPush((PatternProviderLogic) (Object) this, host, actionSource, patternDetails,
                cir.getReturnValueZ());
    }
}
