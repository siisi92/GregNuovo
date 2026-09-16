package com.gregnuovo.core;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.CraftingCpuLogic;

/**
 * 跨 mixin 传递的运行时状态：
 * <ul>
 *     <li>AE 合成 CPU 调用 {@code pushPattern} 期间的上下文（哪个 CPU、哪个样板）；</li>
 *     <li>GT「ME样板总成」每个样板槽独立的编程电路编号。</li>
 * </ul>
 * 只在服务端线程访问。
 */
public final class GNState {

    /** 一次 pushPattern 调用的上下文。 */
    public record PushContext(CraftingCpuLogic cpu, IPatternDetails pattern, Object provider) {}

    private static final ThreadLocal<PushContext> CURRENT_PUSH = new ThreadLocal<>();

    /** 样板总成 -> 每个样板槽的电路编号（-1 表示无电路）。 */
    private static final Map<Object, int[]> SLOT_CIRCUITS = Collections.synchronizedMap(new WeakHashMap<>());

    /** 一次 pushPattern 调用前的输入快照（键 -> 数量），用于推送成功后判断“推了什么”。 */
    private static final ThreadLocal<Map<appeng.api.stacks.AEKey, Long>> HOLDER_SNAPSHOT = new ThreadLocal<>();

    /** 标记当前处于“本模组自己的重试推送”中，避免重复登记。 */
    private static final ThreadLocal<Boolean> REPUSHING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 最近被本模组写过电路的目标 -> (电路编号, 写入时的游戏刻)。
     *
     * <p>用途：{@code circuit.clearWhenAbsent} 只应该在"这台机器上确实没有样板需要电路"时清零。
     * 同一台机器上多个样板在同一 tick 内轮流推料时，没有电路的那个样板会把刚写好的电路抹掉，
     * 表现就是"电路有时不变/压根没写进去"。</p>
     */
    private static final Map<Object, long[]> RECENT_CIRCUITS = Collections.synchronizedMap(new WeakHashMap<>());

    /** 当前游戏刻（拿不到时返回 0）。 */
    public static long now(@Nullable net.minecraft.world.level.Level level) {
        try {
            return level == null ? 0L : level.getGameTime();
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static void markCircuitWritten(@Nullable Object machine, int circuit, long gameTime) {
        if (machine == null) return;
        RECENT_CIRCUITS.put(machine, new long[] { circuit, gameTime });
    }

    /** 最近是否给这台机器写过电路（避免紧接着被别的样板清零）。 */
    public static boolean wasCircuitWrittenRecently(@Nullable Object machine, long gameTime) {
        if (machine == null) return false;
        long[] last = RECENT_CIRCUITS.get(machine);
        if (last == null) return false;
        return gameTime - last[1] <= 40;
    }

    public static void snapshotHolder(appeng.api.stacks.KeyCounter[] holder) {
        Map<appeng.api.stacks.AEKey, Long> map = new java.util.LinkedHashMap<>();
        if (holder != null) {
            for (var counter : holder) {
                if (counter == null) continue;
                for (var entry : counter) {
                    map.merge(entry.getKey(), entry.getLongValue(), Long::sum);
                }
            }
        }
        HOLDER_SNAPSHOT.set(map);
    }

    public static Map<appeng.api.stacks.AEKey, Long> takeHolderSnapshot() {
        var map = HOLDER_SNAPSHOT.get();
        HOLDER_SNAPSHOT.remove();
        return map == null ? new java.util.LinkedHashMap<>() : map;
    }

    public static void clearHolderSnapshot() {
        HOLDER_SNAPSHOT.remove();
    }

    public static boolean isRepushing() {
        return Boolean.TRUE.equals(REPUSHING.get());
    }

    public static void setRepushing(boolean value) {
        if (value) {
            REPUSHING.set(Boolean.TRUE);
        } else {
            REPUSHING.remove();
        }
    }

    public static void pushContext(@Nullable PushContext ctx) {
        if (ctx == null) {
            CURRENT_PUSH.remove();
        } else {
            CURRENT_PUSH.set(ctx);
        }
    }

    @Nullable
    public static PushContext currentPush() {
        return CURRENT_PUSH.get();
    }

    @Nullable
    public static CraftingCpuLogic currentCpu() {
        var ctx = CURRENT_PUSH.get();
        return ctx == null ? null : ctx.cpu();
    }

    /** 读取样板总成某个样板槽的电路编号；-1 表示无。 */
    public static int slotCircuit(Object buffer, int slot, int slotCount) {
        var arr = SLOT_CIRCUITS.get(buffer);
        if (arr == null || slot < 0 || slot >= arr.length) return -1;
        return arr[slot];
    }

    /** 写入样板总成某个样板槽的电路编号；-1 表示清除。 */
    public static void setSlotCircuit(Object buffer, int slot, int slotCount, int circuit) {
        if (buffer == null || slot < 0) return;
        var arr = SLOT_CIRCUITS.computeIfAbsent(buffer, k -> {
            var a = new int[Math.max(slotCount, 1)];
            java.util.Arrays.fill(a, -1);
            return a;
        });
        if (slot < arr.length) {
            arr[slot] = circuit;
        }
    }

    public static void clearSlotCircuits(Object buffer) {
        SLOT_CIRCUITS.remove(buffer);
    }

    /**
     * 找出样板总成里某个样板对应的缓存槽对象。
     *
     * <p>7.3.0 的 {@code detailsSlotMap} 没有公开 getter，这里先用 accessor mixin，
     * 失败再退回反射，保证取不到时不会静默失效。</p>
     */
    @Nullable
    public static Object bufferSlot(Object buffer, Object pattern) {
        if (buffer == null || pattern == null) return null;
        try {
            var map = ((com.gregnuovo.mixin.gtceu.MEPatternBufferAccessor) buffer).GregNuovo$getDetailsSlotMap();
            Object slot = map == null ? null : map.get(pattern);
            if (slot != null) return slot;
        } catch (Throwable ignored) {}
        try {
            for (var field : buffer.getClass().getDeclaredFields()) {
                if (!"detailsSlotMap".equals(field.getName())) continue;
                field.setAccessible(true);
                Object mapObject = field.get(buffer);
                if (mapObject instanceof java.util.Map<?, ?> map) {
                    return map.get(pattern);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 服务器停止时清理。 */
    public static void clearAll() {
        SLOT_CIRCUITS.clear();
        RECENT_CIRCUITS.clear();
        HOLDER_SNAPSHOT.remove();
        CURRENT_PUSH.remove();
        REPUSHING.remove();
    }

    private GNState() {}
}
