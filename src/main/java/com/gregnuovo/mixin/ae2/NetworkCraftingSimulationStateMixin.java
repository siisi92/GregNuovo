package com.gregnuovo.mixin.ae2;

import appeng.api.stacks.AEKey;
import appeng.crafting.inv.NetworkCraftingSimulationState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gregnuovo.config.GNConfig;
import com.gregnuovo.core.PatternAnalyzer;

/**
 * 需求2：让 AE 在<b>规划阶段</b>就认为网络里有 1 个编程电路。
 *
 * <p>为什么需要：AE 的"使用物品列表"（{@code ICraftingPlan.usedItems()}）记的是相对网络的
 * <b>峰值缺口</b>。如果网络里一个电路都没有，模拟抽取会直接返回 0、压根不记缺口，
 * 于是这个电路既不在列表里显示、也不会被 {@code tryExtractInitialItems} 取出来放进 CPU 库存，
 * 结果第一炉就推不出去。</p>
 *
 * <p>这里只在"免库存"模式下、只对电路键、只把 0 补成 1：于是计划里它会显示成 ×1，
 * 提交时由 {@code CraftingCpuHelperMixin} 的 {@code @Redirect} 凭空构造出来交给 CPU，
 * 网络里那个（如果真有）一个都不会少。</p>
 */
@Mixin(NetworkCraftingSimulationState.class)
public abstract class NetworkCraftingSimulationStateMixin {

    @Inject(method = "simulateExtractParent", at = @At("RETURN"), cancellable = true)
    private void GregNuovo$pretendOneCircuit(AEKey what, long amount, CallbackInfoReturnable<Long> cir) {
        if (!GNConfig.circuitInjection() || !GNConfig.circuitStockless()) return;
        if (!PatternAnalyzer.isCircuitKey(what)) return;
        Long current = cir.getReturnValue();
        if (current != null && current > 0) return;
        cir.setReturnValue(1L);
    }
}
