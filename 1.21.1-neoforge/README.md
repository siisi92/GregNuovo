# GregNuovo —— 1.21.1 / NeoForge 版

<img src="src/main/resources/icon.png" width="96" alt="GregNuovo icon">

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-3c8527) ![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-e08a3c) ![GTM](https://img.shields.io/badge/GregTech%20Modern-1.21.1--7.0.x-6b6b6b) ![AE2](https://img.shields.io/badge/Applied%20Energistics%202-19.2.x-2f6f9f) ![License](https://img.shields.io/badge/License-LGPL--3.0-blue) ![Version](https://img.shields.io/badge/version-1.2.0-informational)

**把 AE2 的自动合成和 GregTech Modern 的机器真正接起来的附属模组 —— Minecraft 1.21.1 / NeoForge 版本。**

编程电路自动写进机器 · 概率副产物按需重试 · 不需要的副产物不再空等 · 不消耗的模具/催化剂自动回网 · 样板里写出的副产物也能被 AE 识别与自动合成。

功能与仓库根目录的 1.20.1 / Forge 版**完全一致**，只是把平台从 Forge 迁到 NeoForge、把对接的上游版本换成 1.21.1 的 AE2 19.2.x 与 GTM 1.21.1-7.0.x。

本模组**不添加任何方块和物品**，它只改善「AE2 样板供应器 / GTM ME样板总成」与「GT 机器」之间的配合；支持三种结构：单方块机器、多方块 + 输入总线 / ME输入总线、以及 GTM ME样板总成。

## 功能

| # | 功能 | 说明 |
| --- | --- | --- |
| 1 | **不消耗物品自动回网** | 配方涉及模具、催化剂等 `notConsumable` 输入时，配方结束后把「本次推送但未被消耗」的余料退回 AE 网络 |
| 2 | **编程电路自动写入** | 样板里编码了编程电路就写进机器/总线的电路槽（不作为物品推送、默认不需要网络库存）；样板里没写时，从该机器的 GT 配方**自动推断**所需电路 |
| 3 | **概率产物补料重试** | 当前任务需要的概率产物这次没产出时，机器空闲后自动补料重跑，直至产出（次数可配） |
| 4 | **不需要就不等待** | 样板里编了概率副产物、但本次任务不需要时，主产物到手即完成，不等待也不重试 |
| 5 | **副产物可被 AE 读取** | 样板输出格里写出的每个产物都会登记为「可自动合成」，终端里可直接请求副产物 |

## 环境要求

| 依赖 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x（开发时用 21.1.248） |
| [GregTech Modern](https://github.com/GregTechCEu/GregTech-Modern) | 1.21.1-7.0.x（`mc1.21.1-7.0.2`） |
| [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) | 19.2.x（开发时用 19.2.17） |
| GTM 前置 | LDLib2、Architectury API |
| AE2 前置 | GuideME |
| Java | 21（编译与运行都需要） |

## 目录结构

本目录是一个**独立的 Gradle 工程**（仓库根目录那套是 1.20.1 / Forge 版，两者互不影响）：

```
build.gradle / settings.gradle / gradle.properties   构建脚本
gradlew / gradlew.bat / gradle/                      Gradle Wrapper（8.12）
libs/                                                放上游 mod jar（见 libs/README.md，不提交）
src/main/java/com/gregnuovo/
    GregNuovo.java                                   入口：配置注册、事件、/gregnuovo status
    config/GNConfig.java                             通用配置
    core/                                            五项功能的实现主体
    hook/GNHooks.java                                所有 Mixin 的统一入口
    mixin/ae2/                                       AE2 侧注入（4 处 + 3 个 accessor）
    mixin/gtceu/                                     GTM 侧注入（4 处）
src/main/resources/
    META-INF/neoforge.mods.toml                      模组元数据（含 [[mixins]] 声明）
    gregnuovo.mixins.json                            Mixin 配置
    icon.png / pack.mcmeta
```

## 构建

```bash
# 1) 用 JDK 21 运行 Gradle（gradle.properties 里的 java_version=21）
# 2) 把上游 jar 放进 libs/（见 libs/README.md）
# 3) 构建
./gradlew build

# 产物：build/libs/gregnuovo-1.2.0.jar
```

在国内网络下如果 Gradle 拉不动依赖，可给 Gradle 配镜像，或直接在 IDE 里用同一份 `build.gradle` 同步。

## 安装

1. 把 `build/libs/gregnuovo-1.2.0.jar` 放进实例（或服务端）的 `mods/` 目录；
2. **完全重启游戏**（本模组通过 Mixin 注入，换 jar 必须重启）；
3. 进游戏执行 `/gregnuovo status`（需要 OP），能看到计数表即安装成功。

## 使用

一句话：**把电路和副产物写进样板，其余交给模组**。

* 想要 AE 自动设电路：样板里放不放 `gtceu:programmed_circuit` 都行（不放则按配方自动推断）；
* 想要退模具：按正常方式把模具编进样板输入即可，配方结束后会自动回到网络；
* 想要概率副产物：把副产物也写进样板输出格，然后直接请求它 —— 没出会自动补料，直到产出；
* 只想要主产物：同一块样板直接请求主产物即可，不会为副产物空等。

出问题时用两条线索定位：`/gregnuovo status`（OP，打印各环节计数：钩子是否触发、有没有找到相邻 GT 目标、写入电路、退料扫描、补料重试等）与 `misc.debugLog = true`（日志里会逐条说明判断依据）。若 `未找到` 一直在涨，日志会直接列出样板供应器周围 6 个方块的实际类型。

## 配置

`config/gregnuovo-common.toml`：

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `circuit.injection` | `true` | 需求2 总开关 |
| `circuit.stockless` | `true` | 电路不需要网络库存（临时构造、推送时丢弃） |
| `circuit.autoDetectFromRecipe` | `true` | 样板没编电路时，从 GT 配方自动推断所需电路 |
| `circuit.clearWhenAbsent` | `true` | 配方不需要电路时，把机器电路槽清零 |
| `circuit.clearAfterCraft` | `true` | 合成结束后清除本模组写入的电路 |
| `leftover.enabled` | `true` | 需求1 总开关 |
| `chance.byproductAware` | `true` | 从 GT 配方推断哪些输出是概率产物 |
| `chance.byproductCraftable` | `true` | 需求5：副产物也登记为可合成 |
| `chance.retry` | `true` | 需求3 总开关 |
| `chance.maxRetries` | `0` | 单个合成周期的重试上限，`0` = 不限 |
| `chance.retryIntervalTicks` | `20` | 两次重试的最小间隔（tick） |
| `misc.startTimeoutTicks` | `1200` | 机器迟迟不开工时记录一次日志的阈值 |
| `misc.debugLog` | `false` | 输出每一步的判断日志 |

## 兼容性与已知边界

* 只接管 **GT 机器**（实现 `IRecipeLogicMachine` 的机器与多方块控制器）；AE2 及其它 mod 的机器保持原版行为；
* 目标方块没有可写电路槽时，**不会**剥掉电路物品（保持原版当物品推送），避免配方失配；
* 概率产物的判定来自 GT 配方（用样板输出 + 输入匹配机器可跑的配方）。匹配不到唯一配方时按确定性处理，不重试；
* 重试是**真实投料**，会消耗材料（这是需求3 的语义），可用 `chance.maxRetries` 限制；
* AE2 的合成计划按「每一次合成」计算输入，因此一次作业要用 N 次模具时网络里最好备够 N 个（用完会如数退回，不会被吃掉）；编程电路不受此限制；
* 使用 **ME输入总线** 结构时，喂给多方块的物品本来就是 AE 网络的视图、不落地，此时「没有余料可退」是正确行为（`/gregnuovo status` 会显示 `退料扫描` 有计数、`退回余料` 为 0）。

## 1.20.1 → 1.21.1 移植要点

上游在这两个版本之间改了不少东西，下面是本版本实际改掉的对接点（供排查用）：

| 位置 | 1.20.1 / Forge | 1.21.1 / NeoForge |
| --- | --- | --- |
| 物品栏接口 | `net.minecraftforge.items.IItemHandlerModifiable` | `net.neoforged.neoforge.items.IItemHandlerModifiable` |
| AE 存储能力 | `appeng.capabilities.Capabilities.STORAGE`（`LazyOptional`） | `appeng.api.AECapabilities.ME_STORAGE`（`BlockCapability`，用 `BlockCapabilityCache` 取，**且只接受 `ServerLevel`**） |
| 样板输出 | `IPatternDetails#getOutputs()` 返回 `GenericStack[]` | 返回 `List<GenericStack>` |
| 服务器 tick 事件 | `TickEvent.ServerTickEvent` + `Phase.END` | `net.neoforged.neoforge.event.tick.ServerTickEvent.Post`（`Pre`/`Post` 是子类） |
| 配置 | `ForgeConfigSpec` + `ModLoadingContext` | `net.neoforged.neoforge.common.ModConfigSpec` + `ModContainer#registerConfig` |
| 元数据 | `META-INF/mods.toml` + `mandatory=true` | `META-INF/neoforge.mods.toml` + `type="required"`，Mixin 用 `[[mixins]] config=...` 声明 |
| GT 物品原料 | `Ingredient`（配 `IntCircuitIngredient`） | NeoForge `SizedIngredient`（带数量），所以 `SlotCircuitHandler` 的泛型参数跟着变 |
| GT 配方类 | `com.gregtechceu.gtceu.api.recipe.GTRecipe` | `com.gregtechceu.gtceu.api.recipe.kind.GTRecipe` |
| GT 电路类 | `common.item.IntCircuitBehaviour` | `common.item.behavior.IntCircuitBehaviour`（编号改存数据组件） |
| 网络可合成项 | `NetworkCraftingProviders#addProvider(IGridNode)` | 同名方法多了 `ICraftingProvider` 重载，注入必须写全方法描述符（两条路径都建索引） |

`notConsumable` 的判定**没有变**：1.21.1-7.0.2 里依然是 `Content.chance == 0`（GT 界面显示为 NC），没有新的「催化剂」字段。

## 许可与致谢

* 本模组以 **LGPL-3.0** 发布，完整文本见仓库根目录 [LICENSE](../LICENSE)，源码随仓库公开；
* 通过 Mixin 注入 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)（API 为 MIT、实现为 LGPL-3.0）与 [GregTech Modern](https://github.com/GregTechCEu/GregTech-Modern)（LGPL-3.0）的内部实现，**不包含**两者的任何源码、资源或 jar；
* 运行时链接的是整合包里已有的 AE2 / GTM jar（对应 LGPL-3.0 §4(d)(1) 的"共享库机制"），用户可以自行替换成接口兼容的修改版本；
* 本模组是**非官方附属**，与上述两个团队无隶属或背书关系，也不使用它们的 logo 或贴图；
* "Minecraft" 是 Mojang Studios 的商标，本项目与 Mojang Studios / Microsoft 无关联。
