# 贡献指南

感谢愿意帮忙！这个模组很小，但踩的坑不少，下面几条能省你很多时间。

## 开发环境

* JDK 17（编译目标）——用 JDK 21 跑 Gradle 也可以，`options.release = 17` 已设置
* Gradle 8.8（仓库自带 wrapper）
* 依赖 jar：`libs/` 目录（见 `libs/README.md` 或运行 `scripts/fetch-libs.ps1`）

```bash
./gradlew build          # 产物在 build/libs/
./gradlew runClient      # 开发环境客户端
```

## 代码结构

```
core/     纯逻辑层（不依赖 mixin）：目标识别、退料、重试、需求判定、诊断
hook/     所有 mixin 的统一入口 GNHooks
mixin/    AE2 / GTM 的注入点，尽量保持“只做转发与状态读写”
```

写注入点前请先看 `docs/research/`（对 AE2 15.4.10 与 GTM 7.3.0 的 API 调研，含精确类名/方法/字段）
以及 `docs/mixin-target-audit.txt`（对真实 jar 的核对结果）。

## 提交前请自查

1. `./gradlew build` 通过；
2. **改动注入点后，务必重新核对目标**（AE2/GTM 的类名、方法描述符、私有字段是否还在），
   核对方式参考 `docs/mixin-target-audit.txt` 里用的 `javap` 方法；
3. 新功能请挂上 `/gregnuovo status` 的计数，方便玩家自查（这个模组的绝大部分 issue 都靠它定位）；
4. 面向玩家的行为变化请同时更新 `docs/使用说明.md` 与 `CHANGELOG.md`。

## 报 Bug 请附

* `gtceu` / `ae2` / `GregNuovo` 的版本（`logs/latest.log` 开头即可）
* `/gregnuovo status` 的完整输出（需 OP）
* `misc.debugLog = true` 后复现一次，`logs/latest.log` 里所有含 `GregNuovo` 的行
* 你的结构：样板供应器紧挨的是什么方块（机器本体 / 输入总线 / ME输入总线 / ME样板总成）、
  多方块是什么机器、样板里都有什么

## 许可

贡献的代码将以 LGPL-3.0 发布。请注意不要拷贝 AE2 / GTM 的源码或资源进本仓库，
也不要提交它们的 jar——`libs/` 下的 jar 已被 `.gitignore` 排除，请保持这样。
