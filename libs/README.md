# libs —— 本地编译依赖

本目录的 jar **只用于编译与开发环境运行**，不会打包进 `GregNuovo` 产物；正式游玩时这些 mod 由整合包提供。**jar 已被 `.gitignore` 排除，请不要提交到仓库。**

## 一键获取

```powershell
powershell -ExecutionPolicy Bypass -File scripts\fetch-libs.ps1
```

（装了 PowerShell 7 的话也可以用 `pwsh -File scripts/fetch-libs.ps1`。）

脚本会按下表下载到本目录（文件名必须一致——`build.gradle` 按文件名引用）。如果你电脑上已经有装好这些 mod 的整合包，也可以直接从实例的 `mods/` 目录复制过来，不必联网。

## 文件清单

| 文件 | 版本 | 用途 | 获取方式 |
| --- | --- | --- | --- |
| `gtceu-1.20.1-7.3.0.jar` | GTM 1.20.1-7.3.0 | 编译 + 运行 | GitHub Release `v7.3.0-1.20.1`（GTM 不在 Modrinth） |
| `appliedenergistics2-forge-15.4.10.jar` | AE2 15.4.10 | 编译 + 运行 | Modrinth `ae2`（forge / 1.20.1） |
| `ldlib-forge-1.20.1-1.0.52.jar` | LDLib 1.0.52 | 编译 + 运行 | Modrinth `ldlib`（GTM 必需） |
| `architectury-9.2.14-forge.jar` | Architectury 9.2.14 | 编译 + 运行 | Modrinth `architectury-api`（LDLib 必需） |
| `configuration-forge-1.20.1-3.1.0.jar` | Configuration 3.1.0 | 仅开发运行 | Modrinth `configuration`（GTM 必需） |
| `guideme-20.1.15.jar` | GuideME 20.1.15 | 仅开发运行 | Modrinth `guideme`（AE2 必需） |

`build.gradle` 中的用法：

* `compileOnly`：仅编译期需要（GTM / AE2 / LDLib / Architectury）
* `runtimeOnly`：开发环境运行时需要（上面全部）

## 换版本时

* 换 GTM / AE2 版本后，**必须**用 `javap` 重新核对 `docs/mixin-target-audit.txt` 里列出的所有注入点
  （类名、方法描述符、私有字段、包私有内部类）——本模组注入的是它们的内部实现。
* 同步更新 `gradle.properties`、`build.gradle` 与上表中的文件名/版本。
