package com.gregnuovo;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import com.gregnuovo.config.GNConfig;
import com.gregnuovo.core.CraftTracker;
import com.gregnuovo.core.GNDiagnostics;
import com.gregnuovo.core.GNState;
import com.gregnuovo.core.RecipeChanceResolver;

/**
 * GregNuovo —— GTM(1.21.1-7.0.x) 与 AE2(19.2.x) 的自动合成整合附属。
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

    public GregNuovo(IEventBus modEventBus, ModContainer modContainer) {
        // NeoForge：配置改为由 ModContainer 注册（Forge 的 ModLoadingContext 已移除）
        modContainer.registerConfig(ModConfig.Type.COMMON, GNConfig.SPEC);
        // 事件用「实例 + @SubscribeEvent」注册（NeoForge 的 EventBus 8 对 Class 形式支持有限）
        NeoForge.EVENT_BUS.register(new Events());
        LOGGER.info("{} 已加载（GTM x AE2 自动合成整合）", MOD_NAME);
    }

    /** 服务端事件。 */
    public static final class Events {

        @SubscribeEvent
        public void onServerTick(ServerTickEvent.Post event) {
            CraftTracker.onServerTick(event.getServer());
        }

        @SubscribeEvent
        public void onAddReloadListener(AddReloadListenerEvent event) {
            // 配方重载：丢弃按配方推断的概率产物缓存
            RecipeChanceResolver.invalidate();
        }

        @SubscribeEvent
        public void onDatapackSync(OnDatapackSyncEvent event) {
            RecipeChanceResolver.invalidate();
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            CraftTracker.clear();
            RecipeChanceResolver.invalidate();
            GNState.clearAll();
        }

        /** /gregnuovo status —— 自诊断：判断五项功能是否真的跑起来了（/gtae 为旧名别名）。 */
        @SubscribeEvent
        public void onRegisterCommands(RegisterCommandsEvent event) {
            registerStatusCommand(event, "gregnuovo");
            registerStatusCommand(event, "gtae"); // 1.0.x 时期的命令名，保留为别名
        }

        private static void registerStatusCommand(RegisterCommandsEvent event, String name) {
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
