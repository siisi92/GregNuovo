# GregNuovo

<img src="src/main/resources/icon.png" width="96" alt="GregNuovo icon">

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-3c8527) ![Forge](https://img.shields.io/badge/Forge-47.4.10-e08a3c) ![GTM](https://img.shields.io/badge/GregTech%20Modern-7.3.0-6b6b6b) ![AE2](https://img.shields.io/badge/Applied%20Energistics%202-15.4.x-2f6f9f) ![License](https://img.shields.io/badge/License-LGPL--3.0-blue) ![Version](https://img.shields.io/badge/version-1.1.1-informational)

**把 AE2 的自动合成和 GregTech Modern 的机器真正接起来的附属模组。**

编程电路自动写进机器 · 概率副产物按需重试 · 不需要的副产物不再空等 · 不消耗的模具/催化剂自动回网 · 样板里写出的副产物也能被 AE 识别与自动合成。

本模组**不添加任何方块和物品**，它只改善「AE2 样板供应器 / GTM ME样板总成」与「GT 机器」之间的配合；支持三种结构：单方块机器、多方块 + 输入总线 / ME输入总线、以及 GTM ME样板总成。

> 需要 **Minecraft 1.21.1 / NeoForge** 版本？见 [`1.21.1-neoforge/`](1.21.1-neoforge/) 目录，或直接切到 **`1.21.1-neoforge` 分支**（那条分支把该版本放在仓库根目录，功能完全一致）。

## 功能

| # | 功能 | 说明 |
| --- | --- | --- |
| 1 | **不消耗物品只算一份** | 配方涉及模具、催化剂等 `notConsumable` 输入（物品和流体都算）时，**整个合成任务只占用 1 份**：下单 64 个也只要网络里有 1 个模具就能开工；每炉结束后它连同本炉产物一起被收回合成 CPU，下一炉再推出去 |
| 2 | **编程电路自动写入** | 样板里编码了编程电路就写进机器/总线的电路槽（不作为物品推送、默认不需要网络库存）；**任务期间保持不变，任务结束后才清除**；想恢复「样板没编电路时从配方自动推断」可打开 `circuit.autoDetectFromRecipe` |
| 3 | **概率产物补料重试** | 当前任务需要的概率产物这次没产出时，机器空闲后自动补料重跑，直至产出（次数可配） |
| 4 | **不需要就不等待** | 样板里编了概率副产物、但本次任务不需要时，主产物到手即完成，不等待也不重试 |
| 5 | **副产物可被 AE 读取** | 样板输出格里写出的每个产物都会登记为「可自动合成」，终端里可直接请求副产物 |

## 环境要求

| 依赖 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10+ |
| [GregTech Modern](https://github.com/GregTechCEu/GregTech-Modern) | 1.20.1-7.3.0+ |
| [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) | 15.4.x |
| GTM 前置 | LDLib、Architectury、Configuration |
| AE2 前置 | GuideME |

## 安装

1. 下载 `gregnuovo-1.1.1.jar`；
2. 放进实例（或服务端）的 `mods/` 目录；
3. **完全重启游戏**（本模组通过 Mixin 注入，换 jar 必须重启）；
4. 进游戏执行 `/gregnuovo status`（需要 OP），能看到计数表即安装成功。

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
| `circuit.autoDetectFromRecipe` | `false` | 样板没编电路时，从 GT 配方自动推断所需电路（**1.1.1 起默认关闭**，推断在候选配方不唯一时会猜错） |
| `circuit.clearWhenAbsent` | `true` | 样板不需要电路时把机器电路槽清零（同一台机器刚被别的样板写过电路时跳过，避免抹掉刚写的电路） |
| `circuit.clearAfterCraft` | `true` | **任务结束后**清除本模组写入的电路（不是每炉） |
| `leftover.enabled` | `true` | 需求1 总开关（不消耗物品只算一份 + 每炉收回） |
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
* 需求1 只收回「AE 正在等的东西」（本炉产物 + 那一份不消耗物品）。**不会**把机器清空：GT 机器会缓存 AE 提前推进来的下一炉原料，那些料 AE 已经记账，抽走会让任务缺料卡死；
* 需求1 的判定仍然来自 GT 配方（`Content.chance == 0`，也就是模具/催化剂）。匹配不到配方时按原版处理（每炉都当普通输入算），此时需要备够相应份数；
* 使用 **ME输入总线** 结构时，喂给多方块的物品本来就是 AE 网络的视图、不落地，缺料/收回的表现会和实体输入总线不同。

## 1.1.1 改动（相对 1.1.0）

* **不消耗物品不再被重复计算**：AE 的计划器现在把 notConsumable 输入当成"用完还回来"的容器物品（空桶那种机制），因此**整个合成任务只算 1 份** —— 下单 64 个也只要网络里有 1 个模具就能开工，不再需要备够 64 个；
* **每炉收回**：每炉结束后，把 AE 正在等的（本炉产物 + 那一份不消耗物品）从机器里收回，先交给合成 CPU，剩下的进网络；下一炉再从 CPU 推出去。任务结束时 CPU 会把剩余物品倒回网络；
* **不重复推送**：机器里已经有了的不消耗物品，本次不再推送，直接把那一份还给 CPU（否则会在输入槽里越堆越多）；
* **电路默认只认样板**：`circuit.autoDetectFromRecipe` 默认从 `true` 改为 `false`（推断在候选配方不唯一时会写错电路）。老的整合包如果依赖自动推断，需要手动改回 `true`；
* **电路改成任务级**：任务期间电路保持不变，需要别的电路由样板当场改写，**任务结束后**才清除，消除每炉之间的空窗期；
* **电路不再被误清**：同一台机器刚被别的样板写过电路时，`clearWhenAbsent` 会跳过，避免把刚写好的电路抹掉；
* **电路在"使用物品列表"里只显示 1 份**：电路在 GT 配方里也是 `chance == 0`，所以它同样走"用完还回来"的容器物品语义 —— AE 记的是峰值缺口，因此**下单 64 个也只会显示 `编程电路 ×1`，而且一个都不会真的被消耗**。免库存模式下计划阶段还会把网络里的电路数当作"至少有 1 个"（只在规划时假装，网络里那一个不会被拿走），所以库存为 0 也能开工；推进机器前这一份会被剥下来交还合成 CPU，供下一炉继续使用，任务结束前本模组会把凭空构造的那一份清掉，避免被 AE 倒回网络变成真物品；
* **电路写入校验**：每次写电路后读回一次，不一致会记 `电路读回校验失败` 并打印一次警告；
* `/gregnuovo status` 新增计数：`不消耗输入样板`、`抽回交还`、`已有不重推`、`电路读回校验失败`。

## 许可与致谢

* 本模组以 **LGPL-3.0** 发布，完整文本见 [LICENSE](LICENSE)，源码随仓库公开；
* 通过 Mixin 注入 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)（API 为 MIT、实现为 LGPL-3.0）与 [GregTech Modern](https://github.com/GregTechCEu/GregTech-Modern)（LGPL-3.0）的内部实现，**不包含**两者的任何源码、资源或 jar；
* 运行时链接的是整合包里已有的 AE2 / GTM jar（对应 LGPL-3.0 §4(d)(1) 的"共享库机制"），用户可以自行替换成接口兼容的修改版本；
* 本模组是**非官方附属**，与上述两个团队无隶属或背书关系，也不使用它们的 logo 或贴图；
* "Minecraft" 是 Mojang Studios 的商标，本项目与 Mojang Studios / Microsoft 无关联。
