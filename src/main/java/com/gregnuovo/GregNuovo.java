package com.gregnuovo;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import com.gregnuovo.config.GNConfig;
import com.gregnuovo.core.CraftTracker;
import com.gregnuovo.core.GNDiagnostics;
import com.gregnuovo.core.GNState;
import com.gregnuovo.core.NonConsumableIndex;
import com.gregnuovo.core.RecipeChanceResolver;

/**
 * GregNuovo —— GTM(1.20.1-7.3.0) 与 AE2(15.4.x) 的自动合成整合附属。
 *
 * <p>提供五项能力：</p>
 * <ol>
 *     <li>样板内的不消耗物品在合成结束后退回 AE 网络；</li>
 *     <li>样板内编码的 GT 编程电路自动写入机器电路槽（不作为物品推送、不消耗网络库存）；</li>
 *     <li>当前任务需要的概率产物没有产出时，持续补料重试直至产出；</li>
 *     <li>当前任务不需要的概率产物，主产物到手即完成，不等待、不重试；</li>
 *     <li>样板内写出的副产物可被 AE 网络读取并作为可自动合成的产物。</li>
 * </ol>
 */
@Mod(GregNuovo.MOD_ID)
public final class GregNuovo {

    public static final String MOD_ID = "gregnuovo";
    public static final String MOD_NAME = "GregNuovo";
    public static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal") // ModLoadingContext#get 在 1.20.1 仍是注册配置的官方方式
    public GregNuovo() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GNConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(Events.class);
        LOGGER.info("{} 已加载（GTM x AE2 自动合成整合）", MOD_NAME);
    }

    /** 服务端事件。 */
    public static final class Events {

        private Events() {}

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            CraftTracker.onServerTick(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        }

        @SubscribeEvent
        public static void onAddReloadListener(AddReloadListenerEvent event) {
            // 配方重载：丢弃按配方推断的概率产物缓存与"不消耗输入"缓存
            RecipeChanceResolver.invalidate();
            NonConsumableIndex.invalidate();
        }

        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            RecipeChanceResolver.invalidate();
            NonConsumableIndex.invalidate();
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            CraftTracker.clear();
            RecipeChanceResolver.invalidate();
            NonConsumableIndex.invalidate();
            GNState.clearAll();
        }

        /** /gregnuovo status —— 自诊断：判断五项功能是否真的跑起来了（/gtae 为旧名别名）。 */
        @SubscribeEvent
        public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
            registerStatusCommand(event, "gregnuovo");
            registerStatusCommand(event, "gtae"); // 1.0.x 时期的命令名，保留为别名
        }

        private static void registerStatusCommand(net.minecraftforge.event.RegisterCommandsEvent event, String name) {
            event.getDispatcher().register(
                    net.minecraft.commands.Commands.literal(name)
                            .requires(source -> source.hasPermission(2))
                            .then(net.minecraft.commands.Commands.literal("status")
                                    .executes(context -> {
                                        for (String line : GNDiagnostics.report().split("\n")) {
                                            context.getSource().sendSuccess(
                                                    () -> net.minecraft.network.chat.Component.literal(line), false);
                                        }
                                        return 1;
                                    })));
        }
    }
}
