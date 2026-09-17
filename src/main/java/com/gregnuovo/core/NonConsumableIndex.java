package com.gregnuovo.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * 需求1 的基础：找出一个样板里"要用、但用完不会被消耗"的输入。
 *
 * <p>判定来源是 GT 配方的 {@code Content.chance == 0}（模具、催化剂这类 notConsumable 输入，
 * 物品和流体都算）。</p>
 *
 * <p>这里刻意<b>不</b>依赖机器：AE 在<b>规划阶段</b>（{@code CraftingCalculation}）就要用到这个信息，
 * 而那时候只有样板、没有机器。所以这里扫描全部 GT 配方类型去找匹配的配方，
 * 并按"样板定义"缓存结果。</p>
 */
public final class NonConsumableIndex {

    private static final Set<AEKey> EMPTY = Collections.emptySet();

    /** 样板定义 -> 该样板里不消耗的输入键。 */
    private static final Map<AEItemKey, Set<AEKey>> CACHE = new HashMap<>();

    /** 数据包/配方重载后调用。 */
    public static void invalidate() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    /** 某个键在这个样板里是不是"用了不会被消耗"。 */
    public static boolean isNonConsumable(@Nullable IPatternDetails details, @Nullable AEKey key) {
        if (details == null || key == null) return false;
        Set<AEKey> set = nonConsumableKeys(details);
        if (set.isEmpty()) return false;
        for (AEKey candidate : set) {
            if (RecipeChanceResolver.sameKey(candidate, key)) return true;
        }
        return false;
    }

    /** 这个样板里全部不消耗的输入键（物品 + 流体）。 */
    public static Set<AEKey> nonConsumableKeys(@Nullable IPatternDetails details) {
        if (details == null) return EMPTY;
        AEItemKey definition = details.getDefinition();
        if (definition == null) return EMPTY;

        synchronized (CACHE) {
            Set<AEKey> cached = CACHE.get(definition);
            if (cached != null) return cached;
        }

        Set<AEKey> computed = EMPTY;
        try {
            computed = compute(details);
        } catch (Throwable t) {
            com.gregnuovo.GregNuovo.LOGGER.warn("GregNuovo：推断样板的不消耗输入失败（{}）", definition, t);
        }

        synchronized (CACHE) {
            CACHE.put(definition, computed);
        }
        return computed;
    }

    private static Set<AEKey> compute(IPatternDetails details) {
        List<GenericStack> outputs = PatternAnalyzer.outputs(details);
        List<AEKey> inputKeys = PatternAnalyzer.inputKeys(details);
        if (outputs.isEmpty() || inputKeys.isEmpty()) return EMPTY;

        Set<AEKey> result = new LinkedHashSet<>();
        for (GTRecipeType type : GTRegistries.RECIPE_TYPES) {
            if (type == null) continue;
            try {
                for (var recipe : RecipeChanceResolver.candidates(type, outputs, inputKeys)) {
                    collect(recipe.getInputContents(ItemRecipeCapability.CAP), inputKeys, result);
                    collect(recipe.getInputContents(FluidRecipeCapability.CAP), inputKeys, result);
                }
            } catch (Throwable ignored) {}
        }
        if (result.isEmpty()) return EMPTY;
        return Collections.unmodifiableSet(result);
    }

    /** 把配方里 chance == 0 的输入，且确实出现在样板输入里的键收集起来。 */
    private static void collect(List<Content> contents, List<AEKey> inputKeys, Set<AEKey> out) {
        for (Content content : contents) {
            if (content == null || content.chance != 0) continue; // 0 = notConsumable（GT 界面显示 NC）
            for (AEKey key : keysOf(content)) {
                if (key == null) continue;
                for (AEKey patternKey : inputKeys) {
                    if (!RecipeChanceResolver.sameKey(patternKey, key)) continue;
                    // 编程电路在 GT 配方里也是 chance == 0（circuitMeta 就是这么实现的），
                    // 这里刻意<b>不</b>排除它：让它也当"用完原样还回来"的容器物品，
                    // AE 才会把它算成 1 份（峰值缺口）而不是 N 份（累计消耗）——
                    // 也就是"使用物品列表里只显示、不消耗"。
                    // 它的实际流转由 GNHooks 的推送前剥离 + 交还 CPU 负责，不会真的进机器。
                    out.add(patternKey);
                    break;
                }
            }
        }
    }

    /** 一个 Content 里可能出现的全部物品/流体键。 */
    private static List<AEKey> keysOf(Content content) {
        List<AEKey> keys = new ArrayList<>(1);
        Object value = content.getContent();
        if (value instanceof Ingredient ingredient) {
            for (ItemStack stack : ingredient.getItems()) {
                if (stack.isEmpty()) continue;
                AEItemKey key = AEItemKey.of(stack);
                if (key != null) keys.add(key);
            }
        } else if (value instanceof FluidIngredient ingredient) {
            if (ingredient.stacks != null) {
                for (FluidStack stack : ingredient.stacks) {
                    if (stack.isEmpty()) continue;
                    AEFluidKey key = AEFluidKey.of(stack);
                    if (key != null) keys.add(key);
                }
            }
        }
        return keys;
    }

    /** 供诊断显示用。 */
    public static int cacheSize() {
        synchronized (CACHE) {
            return CACHE.size();
        }
    }

    private NonConsumableIndex() {}
}
