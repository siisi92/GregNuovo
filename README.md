# GregNuovo

<img src="src/main/resources/icon.png" width="96" alt="GregNuovo icon">

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-3c8527) ![Forge](https://img.shields.io/badge/Forge-47.4.10-e08a3c) ![GTM](https://img.shields.io/badge/GregTech%20Modern-7.3.0-6b6b6b) ![AE2](https://img.shields.io/badge/Applied%20Energistics%202-15.4.x-2f6f9f) ![License](https://img.shields.io/badge/License-LGPL--3.0-blue) ![Version](https://img.shields.io/badge/version-1.1.0-informational)

**把 AE2 的自动合成和 GregTech Modern 的机器真正接起来的附属模组。**

编程电路自动写进机器 · 概率副产物按需重试 · 不需要的副产物不再空等 · 不消耗的模具/催化剂自动回网 · 样板里写出的副产物也能被 AE 识别与自动合成。

> 本模组**不添加任何方块和物品**，它只改善「AE2 样板供应器 / GTM ME样板总成」与「GT 机器」之间的配合。
> 支持单方块机器、多方块 + 输入总线 / ME输入总线、以及 GTM ME样板总成三种结构。
>
> **非官方附属**：本项目与 Applied Energistics 2 团队、GregTech Modern (GregTechCEu) 团队无隶属或背书关系，
> 也不包含它们的任何源码、资源或 jar。第三方许可说明见 [THIRD-PARTY.md](THIRD-PARTY.md)。

---

## 功能

| # | 功能 | 说明 |
| --- | --- | --- |
| 1 | **不消耗物品自动回网** | 配方涉及模具、催化剂等 `notConsumable` 输入时，配方结束后把「本次推送但未被消耗」的余料退回 AE 网络 |
| 2 | **编程电路自动写入** | 样板里编码了编程电路就写进机器电路槽（不作为物品推送、默认不需要网络库存）；样板里没写时，从该机器的 GT 配方**自动推断**所需电路 |
| 3 | **概率产物补料重试** | 当前任务需要的概率产物这次没产出时，机器空闲后自动补料重跑，直至产出（次数可配） |
| 4 | **不需要就不等待** | 样板里编了概率副产物、但本次任务不需要时，主产物到手即完成，不等待也不重试 |
| 5 | **副产物可被 AE 读取** | 样板输出格里写出的每个产物都会登记为「可自动合成」，终端里可直接请求副产物 |

工作方式（简版）：

```
玩家请求合成
   └─ AE 合成CPU：规划 → 推样板
        ├─ [单方块机器 / 输入总线 / ME输入总线] 原版 AE2 样板供应器
        │     · 剥掉样板里的编程电路 → 写进机器/总线的电路槽
        │     · 登记本次推送（推了什么 / 期望什么 / 属于哪个合成任务）
        └─ [多方块] GTM ME样板总成
              · 每个样板槽使用自己的电路（虚拟电路槽，替代原版共用电路）
              · 同样登记本次推送
   └─ 机器完成配方（RecipeLogic#onRecipeFinish）
        ├─ 需求4：任务不需要的概率产物 → 不等、不重试
        ├─ 需求3：任务需要但没到手 → 从网络补料重推
        └─ 需求1：机器空闲后把余料退回网络，并清理写入的电路
```

---

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

1. 下载 `gregnuovo-<版本>.jar`；
2. 放进实例（或服务端）的 `mods/` 目录；
3. **完全重启游戏**（本模组通过 Mixin 注入，换 jar 必须重启）；
4. 进游戏执行 `/gregnuovo status`（需要 OP），能看到计数表即安装成功。

## 使用

一句话版：**把电路和副产物写进样板，其余交给模组**。

* 想要 AE 自动设电路：样板里放不放 `gtceu:programmed_circuit` 都行（不放则按配方自动推断）；
* 想要退模具：按正常方式把模具编进样板输入即可；
* 想要概率副产物：把副产物也写进样板输出格，然后直接请求它。

详细的接线方式、每种结构怎么摆、常见问题，见 **[docs/使用说明.md](docs/使用说明.md)**。

## 配置

`config/gregnuovo-common.toml`：

```toml
[GregNuovo.circuit]
	injection = true            # 需求2 总开关
	stockless = true            # 电路不需要网络库存（临时构造、推送时丢弃）
	autoDetectFromRecipe = true # 样板没编电路时，从 GT 配方自动推断所需电路
	clearWhenAbsent = true      # 配方不需要电路时 → 机器电路槽清零
	clearAfterCraft = true      # 合成结束后清除本模组写入的电路

[GregNuovo.leftover]
	enabled = true              # 需求1 总开关

[GregNuovo.chance]
	byproductAware = true       # 从 GT 配方推断哪些输出是概率产物
	byproductCraftable = true   # 需求5：副产物也登记为可合成
	retry = true                # 需求3 总开关
	maxRetries = 0              # 0 = 不限次数
	retryIntervalTicks = 20     # 重试间隔（tick）

[GregNuovo.misc]
	startTimeoutTicks = 1200    # 机器迟迟不开工时的日志阈值
	debugLog = false            # 打开后日志会写每一步判断
```

## 排查

绝大部分问题都能靠两样东西定位：

1. **`/gregnuovo status`**（OP）—— 打印各环节计数：钩子是否触发、有没有找到相邻 GT 目标、写入电路几次、机器是否真的开过工、退料扫描与退回次数、挂起记录卡在哪个阶段；
2. **`misc.debugLog = true`** —— 日志里 `GregNuovo：` 开头的行会说明每一步的判断依据。

对照表与常见现象见 [docs/使用说明.md](docs/使用说明.md) 第 5 节、[docs/需求实现说明.md](docs/需求实现说明.md) 第 10 节。

## 从源码构建

```bash
git clone <本仓库地址>
cd GregNuovo
powershell -ExecutionPolicy Bypass -File scripts\fetch-libs.ps1   # 下载编译所需的依赖 jar 到 libs/（或手动放入，见 libs/README.md）
./gradlew build                     # 产物：build/libs/gregnuovo-<版本>.jar
```

* 需要 JDK 17+（用 JDK 21 运行 Gradle 也可以，编译目标已固定为 17）；
* 依赖 jar 只用于编译与开发运行，**不会打包进产物**，也没有随仓库分发（`.gitignore` 已排除 `libs/*.jar`）；
* 开发环境运行：`./gradlew runClient` / `./gradlew runServer`（`run/` 下需自备 `eula.txt`）；
* 脚本兼容 Windows PowerShell 5.1 与 PowerShell 7；若系统禁止运行脚本，用上面的 `-ExecutionPolicy Bypass`（或先执行 `Set-ExecutionPolicy -Scope Process Bypass`）。

### 发布到你自己的 GitHub 仓库

```powershell
powershell -ExecutionPolicy Bypass -File scripts\publish-to-github.ps1 `
    -RepoUrl https://github.com/<你的用户名>/GregNuovo.git `
    -UserName <提交用名字> -UserEmail <提交用邮箱>
```

脚本会：预检 git 与 GitHub 连通性 → `git init` + 提交（自动拒绝把 `libs/*.jar`、`refsrc/`、`build/` 提交进去） → 设置 `origin` → `git push`。仓库还不存在时可加 `-CreateRepo GregNuovo`（需已安装并登录 GitHub CLI）。只想在本地建好仓库、暂不推送，加 `-SkipPush`。

前置条件只有两个：**装了 Git**，以及**这台机器能访问 github.com**（若 hosts 里把 github 指向了 127.0.0.1，脚本会直接指出文件路径与处理办法）。

## 项目结构

```
src/main/java/com/gregnuovo/
├── GregTechAE.java              模组入口、配置注册、事件与 /gregnuovo 命令
├── config/GNConfig.java       Forge 配置
├── core/                        纯逻辑层（不依赖 Mixin）
│   ├── CraftTracker.java        合成周期跟踪：退料 / 概率产物重试 / 清理
│   ├── PendingCraft.java        一次推送的记录
│   ├── CraftDemand.java         需求4：从合成CPU内部状态推算任务是否真的需要某物
│   ├── RecipeChanceResolver.java 从 GT 配方推断概率产物 / notConsumable 输入 / 所需电路
│   ├── MachineAccess.java       目标识别（机器/零件/控制器）、电路读写、余料提取
│   ├── PatternAnalyzer.java     样板解析（电路、输入展开、输出）
│   ├── SlotCircuitHandler.java  需求2：样板槽独立的虚拟电路槽
│   ├── GNState.java           跨 Mixin 的运行时状态
│   └── GNDiagnostics.java     /gregnuovo status 的计数
├── hook/GNHooks.java          所有 Mixin 的统一入口
└── mixin/
    ├── ae2/                     AE2 注入（合成CPU / 样板供应器 / 合成索引 / accessor）
    └── gtceu/                   GTM 注入（ME样板总成 / 配方完成 / 电路槽）

docs/                            使用说明、实现说明、注入点核对、上游 API 调研
scripts/fetch-libs.ps1           依赖 jar 下载脚本
```

Mixin 注入点全部针对真实 jar 用 `javap` 逐条核对过，结果保存在[docs/mixin-target-audit.txt](docs/mixin-target-audit.txt)；对 AE2 15.4.10 与 GTM 7.3.0 的 API 调研（含精确类名/方法/字段）在 [docs/research](docs/research)；从需求澄清到发布准备的完整过程记录见 [docs/开发全过程.md](docs/开发全过程.md)。

## 兼容性与已知边界

* 只接管 **GT 机器**（实现 `IRecipeLogicMachine` 的机器与多方块控制器）； AE2 及其它 mod 的机器保持原版行为；
* 目标方块没有可写电路槽时，**不会**剥掉电路物品（保持原版当物品推送），避免配方失配；
* 概率产物的判定来自 GT 配方（用样板输出 + 输入匹配机器可跑的配方）。匹配不到唯一配方时按确定性处理，不重试；
* 重试是**真实投料**，会消耗材料（这是需求3 的语义），可用 `chance.maxRetries` 限制；
* AE2 的合成计划按「每一次合成」计算输入，因此一次作业要用 N 次模具时网络里最好备够 N 个（用完会如数退回，不会被吃掉）；编程电路不受此限制；
* 使用 **ME输入总线** 结构时，喂给多方块的物品本来就是 AE 网络的视图、不落地，此时“没有余料可退”是正确行为（`/gregnuovo status` 会显示 `退料扫描` 有计数、`退回余料` 为 0）。

## 版本与验证状态

当前版本 **1.1.0**（1.0.x 时期名为 `GregTech:AE` / mod id `gtae`），变更见 [CHANGELOG.md](CHANGELOG.md)。

* ✅ 离线编译打包通过；注入点对真实 jar 逐条核对；Mixin 配置可被正常发现与准备；
* ✅ 实机验证：需求3/4/5 已确认生效；需求2（含配方自动推断电路）已确认生效；
* ⏳ 需求1（退料）在 1.0.3 起改为三条取料路径，等待更多结构下的实机反馈。

> 开发环境 `./gradlew runServer` 会在 **AE2 自己**的 mixin 处 `FATAL` 退出
> （AE2 15.4.10 生产 jar 的 refmap 缺少 `spatial.MinecraftServerMixin` 的 `@Shadow` 字段映射，
> 开发环境用官方名称无法定位），与本模组无关，正式游玩不受影响。

## 许可与致谢

* 本模组以 **LGPL-3.0** 发布，见 [LICENSE](LICENSE)；第三方组件与合规说明见 [THIRD-PARTY.md](THIRD-PARTY.md)。
* 通过 Mixin 注入 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)（API 为 MIT，实现为 LGPL-3.0）与 [GregTech Modern](https://github.com/GregTechCEu/GregTech-Modern)（LGPL-3.0）的内部实现，**不包含**两者的任何源码或资源；`libs/` 下的 jar 仅为编译依赖，不随仓库分发、也不打包进产物。
* 本项目为非官方附属，与上述两个团队无隶属关系。

---

## English

**GregNuovo** is a small Forge 1.20.1 addon that makes AE2 autocrafting work properly with GregTech Modern machines (single-block machines, multiblocks with input/ME input buses, and GTM's ME Pattern Buffer):

1. non-consumed recipe inputs (molds, catalysts) are returned to the ME network after the craft;
2. the programmed circuit encoded in a pattern is written into the machine's circuit slot (not pushed as an item, no network stock required) — or auto-detected from the GT recipe;
3. chanced byproducts are re-crafted until they actually drop, when the crafting job needs them;
4. byproducts the job does *not* need are never waited for;
5. every output written in a pattern becomes requestable/craftable in the AE network.

No new blocks or items. Built against Forge 47.4.10, GregTech Modern 1.20.1-7.3.0, AE2 15.4.10. Diagnostics: `/gregnuovo status` (OP) plus `misc.debugLog = true`. Licensed under LGPL-3.0.
