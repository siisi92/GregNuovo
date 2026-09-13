package com.gregnuovo.core;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;

/**
 * AE 网络读写。
 */
public final class NetworkHelper {

    /** 从网络抽取物品。 */
    public static long extract(@Nullable IGrid grid, @Nullable IActionSource src, AEKey key, long amount) {
        if (grid == null || amount <= 0) return 0;
        var storage = grid.getStorageService().getInventory();
        var energy = grid.getEnergyService();
        return StorageHelper.poweredExtraction(energy, storage, key, amount, src);
    }

    /** 往网络塞物品。 */
    public static long insert(@Nullable IGrid grid, @Nullable IActionSource src, AEKey key, long amount) {
        if (grid == null || amount <= 0) return 0;
        var storage = grid.getStorageService().getInventory();
        var energy = grid.getEnergyService();
        return StorageHelper.poweredInsert(energy, storage, key, amount, src);
    }

    /** 只做模拟，判断网络里是否有这么多。 */
    public static long simulateExtract(@Nullable IGrid grid, @Nullable IActionSource src, AEKey key, long amount) {
        if (grid == null || amount <= 0) return 0;
        var storage = grid.getStorageService().getInventory();
        return storage.extract(key, amount, Actionable.SIMULATE, src);
    }

    private NetworkHelper() {}
}
