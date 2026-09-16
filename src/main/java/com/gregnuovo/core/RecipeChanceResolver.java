package com.gregnuovo.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * 需求3/4/5 的基础：判断样板里的某个输出在对应 GT 配方里是不是“概率产出”（副产物）。
 *
 * <p>AE2 的样板不保存概率值，因此这里的概率信息全部来自 GT 配方本身：
 * 依据机器可运行的配方类型，找出与样板输入/输出匹配的配方，读取其
 * {@link Content#isChanced()} 的输出。</p>
 */
public final class RecipeChanceResolver {

    /** 配方类型 -> (输出物品 -> 配方列表)。 */
    private static final Map<GTRecipeType, Map<Item, List<GTRecipe>>> INDEX = new WeakHashMap<>();
    /** 匹配结果缓存。 */
    private static final Map<String, Set<AEKey>> CACHE = new HashMap<>();
    /** 电路推断缓存。 */
    private static final Map<String, CircuitHint> CIRCUIT_CACHE = new HashMap<>();

    /**
     * 从 GT 配方推断出来的“电路提示”。
     *
     * @param circuit            唯一确定的电路编号；null 表示没有或无法确定
     * @param recipeNeedsCircuit 候选中至少有一个配方需要编程电路
     * @param ambiguous          候选配方之间电路编号不一致（此时不猜）
     */
    public record CircuitHint(@Nullable Integer circuit, boolean recipeNeedsCircuit, boolean ambiguous) {

        public static final CircuitHint NONE = new CircuitHint(null, false, false);
    }

    /** 数据包/配方重载后调用。 */
    public static void invalidate() {
        synchronized (INDEX) {
            INDEX.clear();
        }
        synchronized (CACHE) {
            CACHE.clear();
        }
        synchronized (CIRCUIT_CACHE) {
            CIRCUIT_CACHE.clear();
        }
    }

    /**
     * 求样板输出的“概率产物键”集合。
     *
     * @return 样板输出中属于 GT 概率产出的键；无法判断时返回空集合（按确定性处理）。
     */
    public static Set<AEKey> chancedOutputs(IPatternDetails details, @Nullable MetaMachine machine) {
        if (machine == null) return Set.of();
        if (!(machine instanceof IRecipeLogicMachine logicMachine)) return Set.of();
        GTRecipeType type = logicMachine.getRecipeType();
        if (type == null) return Set.of();

        List<GenericStack> outputs = PatternAnalyzer.outputs(details);
        if (outputs.isEmpty()) return Set.of();
        List<AEKey> inputKeys = PatternAnalyzer.inputKeys(details);

        String cacheKey = cacheKey(details, type);
        synchronized (CACHE) {
            var cached = CACHE.get(cacheKey);
            if (cached != null) return cached;
        }

        Set<AEKey> chanced = new LinkedHashSet<>();
        for (GTRecipe recipe : candidates(type, outputs, inputKeys)) {
            for (Content content : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
                if (!content.isChanced()) continue;
                if (!(content.getContent() instanceof Ingredient ingredient)) continue;
                for (ItemStack stack : ingredient.getItems()) {
                    if (stack.isEmpty()) continue;
                    AEItemKey key = AEItemKey.of(stack);
                    if (key == null) continue;
                    for (GenericStack out : outputs) {
                        if (sameKey(out.what(), key)) {
                            chanced.add(out.what());
                            break;
                        }
                    }
                }
            }
        }

        Set<AEKey> result = Collections.unmodifiableSet(chanced);
        synchronized (CACHE) {
            CACHE.put(cacheKey, result);
        }
        return result;
    }

    private static String cacheKey(IPatternDetails details, GTRecipeType type) {
        var sb = new StringBuilder(128);
        sb.append(type.getCategory() == null ? "?" : type.getCategory().toString());
        sb.append('|');
        for (var out : PatternAnalyzer.outputs(details)) {
            sb.append(out.what().getId()).append('x').append(out.amount()).append(',');
        }
        sb.append('|');
        for (var in : PatternAnalyzer.inputKeys(details)) {
            sb.append(in.getId()).append(',');
        }
        return sb.toString();
    }

    /** 某个配方类型下、与这个样板匹配的候选配方（需求1 的"不消耗输入"推断也要用）。 */
    static List<GTRecipe> candidates(GTRecipeType type, List<GenericStack> outputs, List<AEKey> inputKeys) {
        Map<Item, List<GTRecipe>> index;
        synchronized (INDEX) {
            index = INDEX.computeIfAbsent(type, RecipeChanceResolver::buildIndex);
        }
        Set<GTRecipe> result = new LinkedHashSet<>();
        for (GenericStack out : outputs) {
            if (!(out.what() instanceof AEItemKey itemKey)) continue;
            for (GTRecipe recipe : index.getOrDefault(itemKey.getItem(), List.of())) {
                if (matches(recipe, outputs, inputKeys)) {
                    result.add(recipe);
                }
            }
        }
        return List.copyOf(result);
    }

    private static Map<Item, List<GTRecipe>> buildIndex(GTRecipeType type) {
        Map<Item, List<GTRecipe>> index = new HashMap<>();
        try {
            for (var entry : type.getCategoryMap().entrySet()) {
                for (GTRecipe recipe : entry.getValue()) {
                    for (Content content : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
                        if (!(content.getContent() instanceof Ingredient ingredient)) continue;
                        for (ItemStack stack : ingredient.getItems()) {
                            if (stack.isEmpty()) continue;
                            index.computeIfAbsent(stack.getItem(), k -> new java.util.ArrayList<>()).add(recipe);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            com.gregnuovo.GregNuovo.LOGGER.warn("构建 GT 配方索引失败（{}）", type, t);
        }
        return index;
    }

    /** 配方是否可能是该样板对应的配方：输出命中 + 非电路输入都能被样板输入满足。 */
    private static boolean matches(GTRecipe recipe, List<GenericStack> outputs, List<AEKey> inputKeys) {
        boolean outputHit = false;
        for (Content content : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
            if (!(content.getContent() instanceof Ingredient ingredient)) continue;
            for (ItemStack stack : ingredient.getItems()) {
                if (stack.isEmpty()) continue;
                AEItemKey key = AEItemKey.of(stack);
                if (key == null) continue;
                for (GenericStack out : outputs) {
                    if (sameKey(out.what(), key)) {
                        outputHit = true;
                        break;
                    }
                }
            }
        }
        if (!outputHit) return false;

        for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
            if (!(content.getContent() instanceof Ingredient ingredient)) continue;
            if (ingredient instanceof IntCircuitIngredient) continue; // 电路由本模组单独处理
            if (ingredient.isEmpty()) continue;
            boolean satisfied = false;
            for (AEKey key : inputKeys) {
                if (key instanceof AEItemKey itemKey && ingredient.test(itemKey.toStack())) {
                    satisfied = true;
                    break;
                }
            }
            if (!satisfied) return false;
        }
        return true;
    }

    /**
     * 需求2 的兜底：样板里没编编程电路时，从 GT 配方推断这台机器该用哪个电路。
     *
     * <p>GT 用 {@code IntCircuitIngredient}（notConsumable）表达“该电路配方”，
     * 其物品形态就是带 {@code Configuration} 标签的编程电路，可直接读出编号。</p>
     */
    public static CircuitHint circuitHint(IPatternDetails details, @Nullable MetaMachine machine) {
        if (!(machine instanceof IRecipeLogicMachine logicMachine)) return CircuitHint.NONE;
        GTRecipeType type = logicMachine.getRecipeType();
        if (type == null) return CircuitHint.NONE;

        String cacheKey = "circuit|" + cacheKey(details, type);
        synchronized (CIRCUIT_CACHE) {
            var cached = CIRCUIT_CACHE.get(cacheKey);
            if (cached != null) return cached;
        }

        List<GenericStack> outputs = PatternAnalyzer.outputs(details);
        List<AEKey> inputKeys = PatternAnalyzer.inputKeys(details);

        boolean needsCircuit = false;
        boolean ambiguous = false;
        Integer found = null;
        for (GTRecipe recipe : candidates(type, outputs, inputKeys)) {
            for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
                if (!(content.getContent() instanceof IntCircuitIngredient circuitIngredient)) continue;
                ItemStack[] items = circuitIngredient.getItems();
                if (items.length == 0 || items[0].isEmpty()) continue;
                int configuration = IntCircuitBehaviour.getCircuitConfiguration(items[0]);
                needsCircuit = true;
                if (found == null) {
                    found = configuration;
                } else if (found != configuration) {
                    ambiguous = true;
                }
            }
        }

        CircuitHint hint = ambiguous ? new CircuitHint(null, true, true)
                : new CircuitHint(found, needsCircuit, false);
        synchronized (CIRCUIT_CACHE) {
            CIRCUIT_CACHE.put(cacheKey, hint);
        }
        return hint;
    }

    /** 两个 AE 键是否指同一个东西（使用 AE2 自身的匹配语义）。 */
    public static boolean sameKey(@Nullable AEKey a, @Nullable AEKey b) {
        if (a == null || b == null) return false;
        return a.matches(new GenericStack(b, 1));
    }

    /**
     * 求样板输入里“不消耗”的物品键（GT 用 {@code Content.chance == 0} 标记 notConsumable，例如模具、催化剂）。
     *
     * @return 同时出现在样板输入里的不消耗键；无法判断时返回空集合
     */
    public static Set<AEKey> notConsumableInputs(IPatternDetails details, @Nullable MetaMachine machine) {
        if (!(machine instanceof IRecipeLogicMachine logicMachine)) return Set.of();
        GTRecipeType type = logicMachine.getRecipeType();
        if (type == null) return Set.of();

        List<GenericStack> outputs = PatternAnalyzer.outputs(details);
        List<AEKey> inputKeys = PatternAnalyzer.inputKeys(details);
        Set<AEKey> result = new LinkedHashSet<>();

        for (GTRecipe recipe : candidates(type, outputs, inputKeys)) {
            for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
                if (content.chance != 0) continue; // 0 = notConsumable
                if (!(content.getContent() instanceof Ingredient ingredient)) continue;
                for (ItemStack stack : ingredient.getItems()) {
                    if (stack.isEmpty()) continue;
                    AEItemKey key = AEItemKey.of(stack);
                    if (key == null) continue;
                    for (AEKey inputKey : inputKeys) {
                        if (sameKey(inputKey, key)) {
                            result.add(inputKey);
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    private RecipeChanceResolver() {}
}
