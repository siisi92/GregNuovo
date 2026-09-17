# libs/ —— 本地依赖 jar（不随仓库分发）

这个目录用来放**只用于编译和开发运行**的上游 mod jar。

这里面的 jar 不会被提交到仓库，也不会被打包进 `gregnuovo` 的产物。

## 需要哪些

| 用途 | mod | 版本 |
| --- | --- | --- |
| 主要依赖 | GregTech CEu Modern | 1.20.1-7.3.0 |
| 主要依赖 | Applied Energistics 2 | 15.4.10（Forge） |
| GTM 前置 | LDLib | `ldlib-forge-1.20.1-*` |
| LDLib 前置 | Architectury API | `architectury-*-forge` |
| 开发运行期 | Configuration | `configuration-forge-1.20.1-*` |
| 开发运行期 | GuideME | `guideme-20.1.*` |

## 怎么放

最省事的做法：从**已经装好这些 mod 的 1.20.1 整合包实例**的 `mods/` 目录里，把对应 jar 复制到本目录。

文件名不必和上表完全一致：`build.gradle` 是按关键字
（`gtceu` / `appliedenergistics2` / `ldlib` / `architectury`）匹配的，带前缀或小版本号不同都能用。

复制完之后：

```bash
./gradlew build
# 产物：build/libs/gregnuovo-<版本>.jar
```

需要 **JDK 17 或更高**（用 JDK 21 跑 Gradle 也可以）。

## 注意

必须是 **1.20.1 / Forge** 的构建。`build.gradle` 会拒绝 1.21.1 / NeoForge 的 jar 并说明原因。

想玩 1.21.1 版请切到 `1.21.1-neoforge` 分支。
