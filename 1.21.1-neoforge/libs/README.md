# libs/ —— 本地依赖 jar（不随仓库分发）

这个目录用来放**只用于编译和开发运行**的上游 mod jar。

这里面的 jar 不会被提交到仓库，也不会被打包进 `gregnuovo` 的产物里。

## 需要哪些

| 用途 | mod | 目标版本 |
| --- | --- | --- |
| 主要依赖 | GregTech CEu Modern | 1.21.1-7.0.x（`mc1.21.1-7.0.2`） |
| 主要依赖 | Applied Energistics 2 | 19.2.x（`19.2.17`） |
| GTM 前置 | LDLib2 | `ldlib2-neoforge-1.21.1-*` |
| LDLib 前置 | Architectury API | `architectury-*-neoforge` |
| 开发运行期 | GuideME | `guideme-21.1.*` |

## 怎么放

最省事的做法：从**已经装好这些 mod 的 1.21.1 整合包实例**的 `mods/` 目录里，把对应 jar 复制到本目录。

文件名不必和上表完全一致，`build.gradle` 是按关键字（`gtceu` / `appliedenergistics2` / `ldlib` / `architectury`）匹配的。

## 注意

必须是 **1.21.1 / NeoForge** 的构建。

1.20.2 起 Minecraft 全程使用 Mojang 官方名（不再有 SRG/reobf），所以旧的 1.20.1 构建无法补救、也不能拿来编译。

同理，`build.gradle` 里没有也不需要 `fg.deobf` / `deobf` 之类的重映射配置。
