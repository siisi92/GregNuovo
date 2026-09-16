package com.gregnuovo.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * GregNuovo 通用配置。
 */
public final class GNConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue CIRCUIT_INJECTION;
    private static final ForgeConfigSpec.BooleanValue CIRCUIT_STOCKLESS;
    private static final ForgeConfigSpec.BooleanValue CIRCUIT_AUTO_DETECT;
    private static final ForgeConfigSpec.BooleanValue CLEAR_CIRCUIT_WHEN_ABSENT;
    private static final ForgeConfigSpec.BooleanValue CLEAR_CIRCUIT_AFTER_CRAFT;
    private static final ForgeConfigSpec.BooleanValue LEFTOVER_RETURN;
    private static final ForgeConfigSpec.BooleanValue CHANCED_RETRY;
    private static final ForgeConfigSpec.IntValue MAX_CHANCED_RETRIES;
    private static final ForgeConfigSpec.BooleanValue BYPRODUCT_AWARE;
    private static final ForgeConfigSpec.BooleanValue BYPRODUCT_CRAFTABLE;
    private static final ForgeConfigSpec.IntValue RETRY_INTERVAL_TICKS;
    private static final ForgeConfigSpec.IntValue START_TIMEOUT_TICKS;
    private static final ForgeConfigSpec.BooleanValue DEBUG_LOG;

    static {
        var b = new ForgeConfigSpec.Builder();

        b.comment("GregNuovo —— AE2 与 GTM(1.20.1) 自动合成整合").push("gregnuovo");

        b.comment("需求2：把样板内编码的 GT 编程电路写入机器电路槽，而不是当作普通物品推送。").push("circuit");
        CIRCUIT_INJECTION = b
                .comment("总开关。关闭后编程电路按原版行为作为物品推送。")
                .define("injection", true);
        CIRCUIT_STOCKLESS = b
                .comment("true：样板里的编程电路不需要网络库存（CPU 阶段会临时构造，推送时丢弃）；",
                        "false：电路从网络库存中借出，推送时立刻原样退回网络（库存不被消耗，但开工时需要至少 1 个备货）。")
                .define("stockless", true);
        CIRCUIT_AUTO_DETECT = b
                .comment("样板里没有编码编程电路时，从该机器可运行的 GT 配方自动推断所需电路并写入机器。",
                        "默认关闭：推断在候选配方不唯一时容易猜错（表现为“电路写错”），",
                        "所以默认只认样板里编码的电路；需要旧行为可以打开。")
                .define("autoDetectFromRecipe", false);
        CLEAR_CIRCUIT_WHEN_ABSENT = b
                .comment("样板里没有编程电路时，把机器电路槽清零（设为“无编程电路”）。",
                        "同一台机器上刚被别的样板写过电路时不会清零，避免把刚写好的电路抹掉。")
                .define("clearWhenAbsent", true);
        CLEAR_CIRCUIT_AFTER_CRAFT = b
                .comment("一次合成任务结束后清除由本模组写入的机器电路，避免影响后续手动操作。",
                        "注意是“任务结束”而不是“每炉结束”：任务期间电路保持不变，",
                        "需要时由下一个样板当场改写，避免每炉之间出现空窗期。")
                .define("clearAfterCraft", true);
        b.pop();

        b.comment("需求1：不消耗物品（模具/催化剂）在合成期间只占一份，每炉用完后收回 AE。").push("leftover");
        LEFTOVER_RETURN = b
                .comment("总开关。",
                        "开启后：AE 的计划阶段就把不消耗输入当成“用完还回来”，整个任务只算 1 份；",
                        "每炉结束后把它连同本炉产物从机器里收回到合成 CPU，下一炉再推出去。")
                .define("enabled", true);
        b.pop();

        b.comment("需求3/4/5：概率产物（副产物）相关行为。").push("chance");
        BYPRODUCT_AWARE = b
                .comment("从 GT 配方自动推断样板里的哪些输出是概率产出（副产物）。",
                        "关闭后本模组不再区分概率产物，全部按原版 AE 的确定性输出处理。")
                .define("byproductAware", true);
        BYPRODUCT_CRAFTABLE = b
                .comment("需求5：把样板里写出的副产物也登记为“可自动合成”。",
                        "AE2 原版只用主产物登记，因此请求副产物时无法使用该样板；开启后可以。")
                .define("byproductCraftable", true);
        CHANCED_RETRY = b
                .comment("需求3：当前合成任务仍需要某个概率产物、但机器这次没有产出时，继续发送材料重试。")
                .define("retry", true);
        MAX_CHANCED_RETRIES = b
                .comment("单个合成周期的最大重试次数；0 表示不限（直至产出所需物品）。")
                .defineInRange("maxRetries", 0, 0, 100000);
        RETRY_INTERVAL_TICKS = b
                .comment("两次重试之间的最小间隔（tick）。")
                .defineInRange("retryIntervalTicks", 20, 1, 1200);
        b.pop();

        b.comment("其它").push("misc");
        START_TIMEOUT_TICKS = b
                .comment("推送后机器迟迟没有开始工作时，记录一次调试日志的阈值（tick）；",
                        "任务仍在进行时会继续等待，任务结束后自动退料并清理。")
                .defineInRange("startTimeoutTicks", 1200, 20, 240000);
        DEBUG_LOG = b.comment("输出调试日志。").define("debugLog", false);
        b.pop();

        b.pop();
        SPEC = b.build();
    }

    public static boolean circuitInjection() {
        return CIRCUIT_INJECTION.get();
    }

    public static boolean circuitStockless() {
        return CIRCUIT_STOCKLESS.get();
    }

    public static boolean circuitAutoDetect() {
        return CIRCUIT_AUTO_DETECT.get();
    }

    public static boolean clearCircuitWhenAbsent() {
        return CLEAR_CIRCUIT_WHEN_ABSENT.get();
    }

    public static boolean clearCircuitAfterCraft() {
        return CLEAR_CIRCUIT_AFTER_CRAFT.get();
    }

    public static boolean leftoverReturn() {
        return LEFTOVER_RETURN.get();
    }

    public static boolean byproductAware() {
        return BYPRODUCT_AWARE.get();
    }

    public static boolean byproductCraftable() {
        return BYPRODUCT_CRAFTABLE.get();
    }

    public static boolean chancedRetry() {
        return CHANCED_RETRY.get();
    }

    public static int maxChancedRetries() {
        return MAX_CHANCED_RETRIES.get();
    }

    public static int retryIntervalTicks() {
        return RETRY_INTERVAL_TICKS.get();
    }

    public static int startTimeoutTicks() {
        return START_TIMEOUT_TICKS.get();
    }

    public static boolean debugLog() {
        return DEBUG_LOG.get();
    }

    private GNConfig() {}
}
