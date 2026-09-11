package com.gregnuovo.mixin.ae2;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;

import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gregnuovo.core.GNState;

/**
 * 在合成 CPU 执行合成时建立上下文，使样板供应器一侧知道“这次推送属于哪个合成任务”。
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
}
