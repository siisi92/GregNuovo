package com.gregnuovo.mixin.ae2;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;

import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gregnuovo.config.GNConfig;
import com.gregnuovo.core.PatternAnalyzer;
import com.gregnuovo.hook.GNHooks;

/**
 * 需求2 / 需求4 在合成 CPU 侧的两处配合：
 * <ul>
 *     <li>样板里的编程电路可以不需要网络库存（临时构造，推送时丢弃）；</li>
 *     <li>把任务不需要的副产物从“等待返回”里剔除。</li>
 * </ul>
 */
@Mixin(CraftingCpuHelper.class)
public abstract class CraftingCpuHelperMixin {

    /**
     * 需求4：样板输出里，凡是当前合成任务不需要的（且不是主产物），不计入 waitingFor。
     */
    @Inject(method = "extractPatternInputs", at = @At("RETURN"))
    private static void GregNuovo$filterExpectedOutputs(IPatternDetails details, ICraftingInventory sourceInv, Level level,
                                                   KeyCounter expectedOutputs, KeyCounter expectedContainerItems,
                                                   CallbackInfoReturnable<KeyCounter[]> cir) {
        GNHooks.filterExpectedOutputs(details, expectedOutputs);
    }

    /**
     * 需求2（stockless 模式）：样板里的编程电路不必占用网络库存。
     * 这里在“按计划抽取开工材料”时对电路直接返回足量（构造出的电路在推送时会被丢弃，不会复制进网络）。
     */
    @Redirect(method = "tryExtractInitialItems", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/MEStorage;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"))
    private static long GregNuovo$fabricateCircuit(MEStorage storage, AEKey what, long amount, Actionable mode,
                                              IActionSource src) {
        if (GNConfig.circuitInjection() && GNConfig.circuitStockless() && PatternAnalyzer.isCircuitKey(what)) {
            return amount;
        }
        return storage.extract(what, amount, mode, src);
    }
}
