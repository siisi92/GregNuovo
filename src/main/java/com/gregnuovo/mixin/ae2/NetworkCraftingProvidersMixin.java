package com.gregnuovo.mixin.ae2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import appeng.me.service.helpers.NetworkCraftingProviders;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gregnuovo.config.GNConfig;

/**
 * 需求5：让 AE 网络能“读到样板内写出的副产物”。
 *
 * <p>AE2 原版只用样板的<b>主产物</b>（{@code getPrimaryOutput()}）登记可合成项，因此样板里另外写出的副产物
 * 无法被合成请求使用。这里为所有输出建立索引：请求副产物时同样能找到该样板，
 * 于是可以自动合成；配合需求3 的重试，概率副产物最终会被产出。</p>
 *
 * <p>1.21.1 变化：{@code addProvider}/{@code removeProvider} 在 AE2 19.x 各有
 * {@code IGridNode} 与 {@code ICraftingProvider} 两个重载（后者用于新增的“全局样板供应器”API），
 * 因此这里必须写全方法描述符，并对两条路径都建立索引。</p>
 */
@Mixin(NetworkCraftingProviders.class)
public abstract class NetworkCraftingProvidersMixin {

    @Unique
    private Map<AEKey, List<IPatternDetails>> GregNuovo$patternsByOutput;

    @Unique
    private Set<AEKey> GregNuovo$craftableKeysCache;

    @Unique
    private Map<AEKey, List<IPatternDetails>> GregNuovo$index() {
        var index = GregNuovo$patternsByOutput;
        if (index == null) {
            index = new HashMap<>();
            GregNuovo$patternsByOutput = index;
        }
        return index;
    }

    @Unique
    private void GregNuovo$indexProvider(@Nullable ICraftingProvider provider) {
        if (!GNConfig.byproductCraftable() || provider == null) return;
        try {
            var index = GregNuovo$index();
            for (IPatternDetails pattern : provider.getAvailablePatterns()) {
                boolean counted = false;
                for (var output : pattern.getOutputs()) {
                    if (output == null || output.what() == null) continue;
                    var list = index.computeIfAbsent(output.what(), k -> new ArrayList<>());
                    if (!list.contains(pattern)) {
                        list.add(pattern);
                        counted = true;
                    }
                }
                if (counted) {
                    com.gregnuovo.core.GNDiagnostics.byproductPatterns.incrementAndGet();
                }
            }
            GregNuovo$craftableKeysCache = null;
        } catch (Throwable ignored) {}
    }

    @Unique
    private void GregNuovo$unindexProvider(@Nullable ICraftingProvider provider) {
        if (provider == null) return;
        var index = GregNuovo$patternsByOutput;
        if (index == null) return;
        try {
            for (IPatternDetails pattern : provider.getAvailablePatterns()) {
                for (var it = index.values().iterator(); it.hasNext();) {
                    var list = it.next();
                    list.remove(pattern);
                    if (list.isEmpty()) it.remove();
                }
            }
            GregNuovo$craftableKeysCache = null;
        } catch (Throwable ignored) {}
    }

    // 路径一：网格上的供应器（AE2 样板供应器 / GTM ME样板总成）

    @Inject(method = "addProvider(Lappeng/api/networking/IGridNode;)V", at = @At("TAIL"))
    private void GregNuovo$indexProviderByNode(IGridNode node, CallbackInfo ci) {
        GregNuovo$indexProvider(node == null ? null : node.getService(ICraftingProvider.class));
    }

    @Inject(method = "removeProvider(Lappeng/api/networking/IGridNode;)V", at = @At("TAIL"))
    private void GregNuovo$unindexProviderByNode(IGridNode node, CallbackInfo ci) {
        GregNuovo$unindexProvider(node == null ? null : node.getService(ICraftingProvider.class));
    }

    // 路径二：全局供应器（AE2 19.x 新增的 ICraftingService#addGlobalCraftingProvider）

    @Inject(method = "addProvider(Lappeng/api/networking/crafting/ICraftingProvider;)V", at = @At("TAIL"))
    private void GregNuovo$indexGlobalProvider(ICraftingProvider provider, CallbackInfo ci) {
        GregNuovo$indexProvider(provider);
    }

    @Inject(method = "removeProvider(Lappeng/api/networking/crafting/ICraftingProvider;)V", at = @At("TAIL"))
    private void GregNuovo$unindexGlobalProvider(ICraftingProvider provider, CallbackInfo ci) {
        GregNuovo$unindexProvider(provider);
    }

    @Inject(method = "getCraftingFor", at = @At("RETURN"), cancellable = true)
    private void GregNuovo$includeByproductPatterns(AEKey whatToCraft,
                                                    CallbackInfoReturnable<Collection<IPatternDetails>> cir) {
        var current = cir.getReturnValue();
        if (current != null && !current.isEmpty()) return;
        var index = GregNuovo$patternsByOutput;
        if (index == null) return;
        var extra = index.get(whatToCraft);
        if (extra != null && !extra.isEmpty()) {
            cir.setReturnValue(Collections.unmodifiableList(new ArrayList<>(extra)));
        }
    }

    @Inject(method = "getCraftableKeys", at = @At("RETURN"), cancellable = true)
    private void GregNuovo$includeByproductKeys(CallbackInfoReturnable<Set<AEKey>> cir) {
        var index = GregNuovo$patternsByOutput;
        if (index == null || index.isEmpty()) return;
        var cached = GregNuovo$craftableKeysCache;
        if (cached == null) {
            Set<AEKey> keys = new HashSet<>(cir.getReturnValue());
            keys.addAll(index.keySet());
            cached = Collections.unmodifiableSet(keys);
            GregNuovo$craftableKeysCache = cached;
        }
        cir.setReturnValue(cached);
    }

    @Inject(method = "getCraftables", at = @At("RETURN"), cancellable = true)
    private void GregNuovo$includeByproductCraftables(AEKeyFilter filter, CallbackInfoReturnable<Set<AEKey>> cir) {
        var index = GregNuovo$patternsByOutput;
        if (index == null || index.isEmpty()) return;
        var result = cir.getReturnValue();
        result = result == null ? new HashSet<>() : new HashSet<>(result);
        for (AEKey key : index.keySet()) {
            if (filter == null || filter.matches(key)) {
                result.add(key);
            }
        }
        cir.setReturnValue(result);
    }
}
