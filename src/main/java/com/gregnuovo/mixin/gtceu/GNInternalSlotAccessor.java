package com.gregnuovo.mixin.gtceu;

import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 需求1（多方块）：直接读写 ME样板总成里某个样板槽的缓冲内容。
 *
 * <p>7.3.0 的 {@code InternalSlot} 只有 {@code refund()}（把整槽退回去）和只读的
 * {@code getItems()/getFluids()}，没有"按物品取走一部分"的公开方法。
 * 但槽内容其实就是两张 {@code Object2LongMap}，这里用 accessor 拿到它们，
 * 就能只抽走 AE 正在等的那一份（不消耗物品），而不是把下一炉的料一起卷走。</p>
 */
@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine$InternalSlot")
public interface GNInternalSlotAccessor {

    @Accessor("itemInventory")
    Object2LongOpenCustomHashMap<ItemStack> GregNuovo$getItemInventory();

    @Accessor("fluidInventory")
    Object2LongOpenHashMap<FluidStack> GregNuovo$getFluidInventory();
}
