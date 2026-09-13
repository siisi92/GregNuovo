package com.gregnuovo.core;

import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IFilteredHandler;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.kind.GTRecipe;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 需求2（多方块）：样板总成里“每个样板槽独立的电路”。
 *
 * <p>GTM 原生的 ME样板总成 把所有样板槽共用同一个电路槽，多个不同电路的样板会互相干扰。
 * 这里为每个样板槽插入一个虚拟电路处理槽：把该样板槽自己的电路编号当作“机器上插着的编程电路”
 * 提供给配方匹配，不会真的放入物品、也不会被消耗；样板槽空了就视为电路已清除。</p>
 *
 * <p>1.21.1 变化：GT 的物品处理能力从 {@code IRecipeHandler<Ingredient>} 变成了
 * {@code IRecipeHandler<SizedIngredient>}（NeoForge 带数量的原料类型），配方类也搬到了
 * {@code api.recipe.kind.GTRecipe}。</p>
 */
public final class SlotCircuitHandler implements IRecipeHandler<SizedIngredient> {

    private final Supplier<ItemStack> circuitSupplier;

    public SlotCircuitHandler(Supplier<ItemStack> circuitSupplier) {
        this.circuitSupplier = circuitSupplier;
    }

    @Nullable
    private ItemStack circuit() {
        ItemStack stack = circuitSupplier == null ? null : circuitSupplier.get();
        return stack == null || stack.isEmpty() ? null : stack;
    }

    @Override
    public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left,
                                                   boolean simulate) {
        if (io != IO.IN || left == null || left.isEmpty()) return left;
        ItemStack circuit = circuit();
        if (circuit == null) return left;

        for (var it = left.iterator(); it.hasNext();) {
            SizedIngredient sized = it.next();
            if (sized == null || sized.ingredient().isEmpty()) continue;
            if (sized.ingredient().test(circuit)) {
                // 电路只是“机器配置”，匹配上即算满足，且不消耗
                it.remove();
            }
        }
        return left.isEmpty() ? null : left;
    }

    @Override
    public List<Object> getContents() {
        ItemStack circuit = circuit();
        return circuit == null ? List.of() : List.of(circuit);
    }

    @Override
    public double getTotalContentAmount() {
        return circuit() == null ? 0 : 1;
    }

    @Override
    public RecipeCapability<SizedIngredient> getCapability() {
        return ItemRecipeCapability.CAP;
    }

    @Override
    public boolean isDistinct() {
        return true;
    }

    @Override
    public int getPriority() {
        return IFilteredHandler.HIGH;
    }

    /** 便捷构造：给定电路编号（-1 表示无电路）。 */
    public static SlotCircuitHandler of(Supplier<Integer> circuitNumber) {
        return new SlotCircuitHandler(() -> {
            Integer number = circuitNumber == null ? null : circuitNumber.get();
            if (number == null || number < 0) return ItemStack.EMPTY;
            return IntCircuitBehaviour.stack(Math.min(number, IntCircuitBehaviour.CIRCUIT_MAX));
        });
    }
}
