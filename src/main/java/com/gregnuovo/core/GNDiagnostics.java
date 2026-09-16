package com.gregnuovo.core;

import java.util.concurrent.atomic.AtomicLong;

import com.gregnuovo.config.GNConfig;

/**
 * 运行期自诊断计数：用 {@code /GregNuovo status} 查看。
 *
 * <p>用途：判断每一项功能是否真的跑起来了——如果某个计数一直是 0，说明对应的注入点没有生效
 * 或者前置条件没满足（例如「找不到相邻 GT 目标」）。</p>
 */
public final class GNDiagnostics {

    /** AE2 样板供应器 pushPattern 钩子被调用次数（单体机器一侧）。 */
    public static final AtomicLong providerPushes = new AtomicLong();
    /** GTM ME样板总成 pushPattern 钩子被调用次数（多方块一侧）。 */
    public static final AtomicLong bufferPushes = new AtomicLong();
    /** 成功找到相邻 GT 目标（机器 / 零件 / 控制器）的次数。 */
    public static final AtomicLong targetsResolved = new AtomicLong();
    /** 找不到相邻 GT 目标的次数。 */
    public static final AtomicLong targetsMissing = new AtomicLong();
    /** 写入编程电路次数。 */
    public static final AtomicLong circuitsWritten = new AtomicLong();
    /** 其中：电路来自样板里编码的编程电路。 */
    public static final AtomicLong circuitsFromPattern = new AtomicLong();
    /** 其中：样板里没电路，由 GT 配方自动推断得到。 */
    public static final AtomicLong circuitsAutoDetected = new AtomicLong();
    /** 需要写电路但目标没有可写电路槽（此时电路按原版作为物品推送）。 */
    public static final AtomicLong circuitNoSlot = new AtomicLong();
    /** 清除编程电路次数。 */
    public static final AtomicLong circuitsCleared = new AtomicLong();
    /** 登记合成跟踪记录次数。 */
    public static final AtomicLong tracked = new AtomicLong();
    /** 退回余料的物品个数。 */
    public static final AtomicLong leftoversReturned = new AtomicLong();
    /** 概率产物补料重试次数。 */
    public static final AtomicLong retries = new AtomicLong();
    /** 观察到机器真正开工（进入 WORKING）的次数——用于判断配方到底有没有跑起来。 */
    public static final AtomicLong machineStarted = new AtomicLong();
    /** 收到“配方完成”回调并命中跟踪记录的次数。 */
    public static final AtomicLong recipesFinished = new AtomicLong();
    /** 执行退料清理的次数（无论有没有真的取到余料）。 */
    public static final AtomicLong cleanupRuns = new AtomicLong();
    /** 退料扫描次数（每次清理扫一遍机器）。 */
    public static final AtomicLong leftoverScans = new AtomicLong();
    /** 副产物被额外登记为“可自动合成”的样板数。 */
    public static final AtomicLong byproductPatterns = new AtomicLong();
    /** 需求4 中从等待列表里剔除的输出次数。 */
    public static final AtomicLong filteredOutputs = new AtomicLong();
    /** 需求1：识别出"不消耗输入"并套上"容器物品"包装的样板数（AE 会因此只算 1 份）。 */
    public static final AtomicLong containerPatterns = new AtomicLong();
    /** 需求1：从机器里抽回并交还给 AE 的物品/流体个数。 */
    public static final AtomicLong reclaimed = new AtomicLong();
    /** 需求1：机器里已经有了、因此不再重复推送的不消耗物品个数。 */
    public static final AtomicLong withheld = new AtomicLong();
    /** 需求2：电路写入后读回校验失败的次数。 */
    public static final AtomicLong circuitVerifyFailed = new AtomicLong();

    public static String report() {
        var sb = new StringBuilder();
        sb.append("GregNuovo 诊断\n");
        sb.append("配置：电路注入=").append(GNConfig.circuitInjection())
                .append("（免库存=").append(GNConfig.circuitStockless()).append('）')
                .append(" 退余料=").append(GNConfig.leftoverReturn())
                .append(" 概率产物=").append(GNConfig.byproductAware())
                .append(" 重试=").append(GNConfig.chancedRetry())
                .append("（上限=").append(GNConfig.maxChancedRetries()).append('）')
                .append(" 副产物可合成=").append(GNConfig.byproductCraftable()).append('\n');
        sb.append("样板供应器钩子=").append(providerPushes.get())
                .append(" 样板总成钩子=").append(bufferPushes.get()).append('\n');
        sb.append("找到 GT 目标=").append(targetsResolved.get())
                .append(" 未找到=").append(targetsMissing.get()).append('\n');
        sb.append("写入电路=").append(circuitsWritten.get())
                .append("（样板编码=").append(circuitsFromPattern.get())
                .append(" 配方推断=").append(circuitsAutoDetected.get())
                .append(" 无电路槽=").append(circuitNoSlot.get()).append('）')
                .append(" 清除电路=").append(circuitsCleared.get()).append('\n');
        sb.append("跟踪中的合成=").append(CraftTracker.pendingCount())
                .append(" 累计登记=").append(tracked.get()).append('\n');
        sb.append("机器开工=").append(machineStarted.get())
                .append(" 配方完成=").append(recipesFinished.get())
                .append(" 清理次数=").append(cleanupRuns.get()).append('\n');
        sb.append("退回余料=").append(leftoversReturned.get())
                .append(" 退料扫描=").append(leftoverScans.get())
                .append(" 补料重试=").append(retries.get()).append('\n');
        sb.append("副产物登记样板=").append(byproductPatterns.get())
                .append(" 剔除多余等待=").append(filteredOutputs.get()).append('\n');
        sb.append("不消耗输入样板=").append(containerPatterns.get())
                .append(" 抽回交还=").append(reclaimed.get())
                .append(" 已有不重推=").append(withheld.get()).append('\n');
        sb.append("电路读回校验失败=").append(circuitVerifyFailed.get()).append('\n');
        sb.append(CraftTracker.describePending());
        return sb.toString();
    }

    private GNDiagnostics() {}
}
