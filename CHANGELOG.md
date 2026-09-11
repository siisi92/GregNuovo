# Changelog

本项目遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)（`主.次.修订`）。

## [1.1.0] — 项目更名为 GregNuovo

### 变更（破坏性）
* 项目与模组更名为 **GregNuovo**（原名 `GregTech:AE`）：
  * 模组 id：`gtae` → `gregnuovo`（配置文件随之变为 `config/gregnuovo-common.toml`）
  * Java 包：`com.gtae` → `com.gregnuovo`；主类 `GregTechAE` → `GregNuovo`
  * Mixin 配置：`gtae.mixins.json` → `gregnuovo.mixins.json`
  * 命令：`/gregnuovo status`（`/gtae status` 保留为别名）
  * 产物：`gregnuovo-1.1.0.jar`
  * 本模组不注册任何方块/物品，因此**存档安全**；唯一需要重新生成的是配置文件。
* 新增模组图标（`icon.png`，64×64），已在 `mods.toml` 中通过 `logoFile` 引用。

### 文档
* 新增 [THIRD-PARTY.md](THIRD-PARTY.md)：列出上游组件（AE2 的 API 为 MIT、实现为 LGPL-3.0；GTM 为 LGPL-3.0 等）与本模组的合规做法（不分发上游代码/jar、运行时动态链接、随附许可证文本）。
* README 增加「非官方附属、与 AE2 / GTM 团队无隶属关系」声明；模组描述里也加了同样的说明。
* 修复 `gradle.properties` 的编码（此前被一次本地替换写坏，含非法 UTF-8 字节）。
* 新增 `docs/开发全过程.md`（从需求澄清到发布准备的完整记录）。
* **统一 Markdown 排版以适应 GitHub 渲染**：每个段落/列表项合并为单独一行（GitHub 会把段落内的单换行当空格并成一段），列表与表格前补空行，并修掉两处会导致表格错行的问题（表格单元格里的行内代码含未转义 `|`；一行少了一个 `|`）。修复脚本：`scripts/reflow-markdown.ps1`（幂等，支持 `-DryRun`），约定见 `CONTRIBUTING.md`。

### 工具
* 新增 `scripts/publish-to-github.ps1`：一键完成 git 初始化、提交、设置远端与推送（预检 git 与 GitHub 连通性；拒绝提交依赖 jar / 上游源码 / 构建产物；支持 `-CreateRepo`、`-SkipPush`）。
* `scripts/fetch-libs.ps1` 加固：TLS 1.2、连通性预检（能识别 hosts 屏蔽导致的假连通）、单个 jar 失败不影响其余、失败时逐个给出手动下载地址、新增 `-CheckOnly`。
* 两个脚本均改为 **UTF-8 with BOM + CRLF** 并兼容 **Windows PowerShell 5.1**（默认无 `pwsh` 的环境），文档中的调用方式改为 `powershell -ExecutionPolicy Bypass -File ...`。

## [1.0.3] — 退料走三条取料路径

### 修复
* **需求1（不消耗物品退料）在部分结构下无效**：原来只从 Forge 物品栏抽取余料，而 GT 机器的输入槽对外常常是“只进不出”，ME输入总线的库存则挂在 AE 存储上。现在依次尝试：**GT 内部处理槽**（`MetaMachine#getTraits()` 里的 `NotifiableItemStackHandler.storage`，绕过能力/侧面权限）→ **AE 存储**（`Capabilities.STORAGE`）→ **Forge 物品栏**。
* 过滤放宽：任务已结束时，若“不消耗键”过滤后一无所获，退而取回所有本次推送且仍在机器里的余料（仍在 `pushed` 预算内，不会多拿）。

### 新增
* `退料扫描` 计数；调试日志会打印收料方块类型、推送数与不消耗键数，便于定位“没料可退”还是“没扫描”。

## [1.0.2] — 从配方自动推断编程电路

### 新增
* `circuit.autoDetectFromRecipe`（默认开）：样板里**没有**编码编程电路时，从该机器可运行的 GT 配方推断所需电路编号并写入机器（多个候选不一致时不猜，保持现状）。这对应需求2 的原文「当**配方**涉及编程电路时，AE 会自动把机器变成对应电路」。
* `/gregnuovo status` 增加 `机器开工 / 配方完成 / 清理次数` 计数与**挂起记录列表**；电路计数拆分 `样板编码 / 配方推断 / 无电路槽`，无电路槽时打 WARN。

### 变更
* 机器迟迟不开工时不再直接放弃跟踪：任务仍在进行就继续等待，任务结束后自动转入清理并退回余料。

## [1.0.1] — 支持多方块零件作为推料目标

### 修复
* **需求1/2 在「供应器 → 输入总线 / ME输入总线」结构下完全无效**：目标识别原来只认 `IRecipeLogicMachine`（单方块机器/控制器），零件被跳过导致整条机器侧逻辑静默失效。现在把目标拆成 `itemHost`（真正收料，用于退料/补料）与 `workMachine`（真正跑配方，用于状态判断），并把电路写进该总线自己的电路槽。
* 只有确实写入电路槽之后才剥掉电路物品；写不进去时保持原版行为，避免“剥了电路又没写进去”导致配方失配。
* 样板总成的虚拟电路槽增加兼容回退：样板没有自己的电路时沿用玩家在共用电路槽上的设置。
* 样板槽下标改为 accessor + 反射双路径，取不到不再静默失效。

### 新增
* 命令 `/gregnuovo status`（自诊断计数）与关键节点的首/末次日志。

## [1.0.0] — 首个版本

### 新增
* 需求1：合成结束后把「本次推送但未被消耗」的余料退回 AE 网络。
* 需求2：样板里的编程电路写进机器电路槽（不作为物品推送、默认不需要网络库存）。
* 需求3：概率产物未产出时补料重试，直至产出。
* 需求4：任务不需要的概率产物不等待、不重试（主产物到手即完成）。
* 需求5：样板里写出的副产物也登记为可自动合成，可被终端请求。
