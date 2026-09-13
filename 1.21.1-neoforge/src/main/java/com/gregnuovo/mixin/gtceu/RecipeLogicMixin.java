package com.gregnuovo.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gregnuovo.hook.GNHooks;

/**
 * 精确捕捉“一次配方完成”的时刻：概率产物是否产出、余料是否产生都在此刻定下来。
 * （只靠轮询状态在多炉连续运行时可能永远不会看到空闲。）
 */
@Mixin(RecipeLogic.class)
public abstract class RecipeLogicMixin {

    @Shadow
    @Final
    public IRecipeLogicMachine machine;

    @Inject(method = "onRecipeFinish", at = @At("TAIL"))
    private void GregNuovo$onRecipeFinish(CallbackInfo ci) {
        if (machine instanceof MetaMachine metaMachine) {
            GNHooks.onRecipeFinished(metaMachine);
        }
    }
}
