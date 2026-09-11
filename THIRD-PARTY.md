# 第三方组件与许可

本模组**不包含**任何上游模组的源码、资源或 jar。它通过「编译期引用 API + 运行时 Mixin 注入」与下列项目协作，发布产物（`gregnuovo-*.jar`）内只有本仓库自己的代码与资源。

| 项目 | 版本 | 许可 | 本模组如何使用 |
| --- | --- | --- | --- |
| Applied Energistics 2 | 15.4.x | `appeng/api/**` = **MIT**；其余实现 = **LGPL-3.0** | 编译期引用其 API；运行时注入样板供应器与合成 CPU 的内部实现 |
| GregTech Modern (GTCEu) | 1.20.1-7.3.0 | **LGPL-3.0** | 编译期引用其 API；运行时注入 ME样板总成 与 配方逻辑 的内部实现 |
| LDLib / Architectury | 1.0.52 / 9.2.14 | LDLib = GPL-3.0，Architectury = LGPL-3.0 | 仅作为 GTM 的传递依赖出现在编译/开发环境；**本模组代码不引用其任何类型**，也不分发 |
| Configuration / GuideME | 3.1.0 / 20.1.15 | MIT / 见上游仓库 | 仅开发环境运行依赖（GTM / AE2 的前置），不随本仓库与产物分发 |
| Minecraft Forge | 47.4.10 | LGPL-2.1 | 模组加载与事件总线 |

## 许可证合规

* 本模组自身以 **LGPL-3.0** 发布，完整文本见 [`LICENSE`](LICENSE)，源码随仓库公开（即使把 Mixin 注入视为“对库的修改”，LGPL-3.0 §2 的要求也已满足）；
* 本模组**不分发** AE2 / GTM 的任何代码或 jar：`libs/*.jar` 已被 `.gitignore` 排除，发布产物 jar 内只有 `com/gregnuovo/**` 与本模组资源，另附 `META-INF/LICENSE-GregNuovo`；
* 运行时链接的是整合包里已有的 AE2 / GTM jar（对应 LGPL-3.0 §4(d)(1) 的“共享库机制”），用户可以自行替换成接口兼容的修改版本；
* 若你以整合包形式**同时分发**本模组与 AE2 / GTM，请按 LGPL-3.0 §4(b) 随附[GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.txt) 与[GNU LGPL v3](https://www.gnu.org/licenses/lgpl-3.0.txt) 文本（AE2 / GTM 自身的发布物中也已包含其许可声明）。

## 声明

本模组是**非官方附属**，与 Applied Energistics 2 团队、GregTech Modern (GregTechCEu) 团队均无隶属、合作或背书关系。名称中出现的 “GregTech”“Applied Energistics”“AE2” 仅用于说明兼容对象；本项目**不使用**上述项目的 logo、贴图或其它美术资源。

“Minecraft” 是 Mojang Studios 的商标；本项目与 Mojang Studios / Microsoft 无关联。
