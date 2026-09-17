package com.gregnuovo.mixin.ae2;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;

import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gregnuovo.core.GNState;
import com.gregnuovo.hook.GNHooks;

/**
 * 合成 CPU 侧的两件事：
 * <ul>
 *     <li>执行合成时建立上下文，使样板供应器一侧知道"这次推送属于哪个合成任务"；</li>
 *     <li>任务即将结束时，先清掉本模组凭空构造的东西，避免紧接着的"倒回网络"把它们变成真物品。</li>
 * </ul>
 */
@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuLogicMixin {

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void GregNuovo$beginExecute(int maxPatterns, CraftingService craftingService, IEnergyService energyService,
                                   Level level, CallbackInfoReturnable<Integer> cir) {
        GNState.pushContext(new GNState.PushContext((CraftingCpuLogic) (Object) this, null, null));
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"))
    private void GregNuovo$endExecute(CallbackInfoReturnable<Integer> cir) {
        GNState.pushContext(null);
        GNState.clearHolderSnapshot();
    }

    /** finishJob 会紧接着调用 storeItems() 把 CPU 库存倒回网络，所以必须赶在它之前清。 */
    @Inject(method = "finishJob", at = @At("HEAD"))
    private void GregNuovo$beforeFinishJob(boolean success, CallbackInfo ci) {
        GNHooks.onJobFinishing((CraftingCpuLogic) (Object) this);
    }
}
