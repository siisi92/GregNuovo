package com.gregnuovo.mixin.gtceu;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gregnuovo.config.GNConfig;
import com.gregnuovo.core.GNState;
import com.gregnuovo.core.MachineAccess;
import com.gregnuovo.core.SlotCircuitHandler;

/**
 * 需求2（多方块）：为样板总成的每个样板槽提供独立电路。
 *
 * <p>GTM 7.3.0 的 {@code SlotRHL} 构造器把同一个共享电路槽加进了全部样板槽的处理槽列表，
 * 导致所有样板共用一个电路号。这里把共享电路槽摘掉，换成读取该槽位自身电路编号的虚拟电路槽。</p>
 */
@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.trait.InternalSlotRecipeHandler$SlotRHL")
public abstract class SlotRHLMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void GregNuovo$installSlotCircuit(MEPatternBufferPartMachine buffer,
                                         MEPatternBufferPartMachine.InternalSlot slot, int index,
                                         CallbackInfo ci) {
        if (!GNConfig.circuitInjection()) return;

        RecipeHandlerList self = (RecipeHandlerList) (Object) this;
        var itemHandlers = self.getHandlerMap().get(ItemRecipeCapability.CAP);
        if (itemHandlers != null) {
            itemHandlers.removeIf(handler -> handler == buffer.getCircuitInventory());
        }

        self.addHandler(SlotCircuitHandler.of(() -> {
            // 样板槽空了（配方做完）= 电路清除
            if (slot.isItemEmpty() && slot.isFluidEmpty()) return -1;
            int own = GNState.slotCircuit(buffer, index, buffer.getInternalInventory().length);
            if (own >= 0) return own;
            // 兼容原版行为：该样板没有自己的电路时，沿用玩家在共用电路槽上的设置
            return MachineAccess.getCircuit(buffer);
        }));
    }
}
