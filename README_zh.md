# Despotes

> **Despotes** —— 一个 Minecraft: Java Edition 模组，在游戏进程内开启本地控制通道，让外部控制源驱动游戏，且**完全不占用用户的键盘与鼠标**。

本文件为中文副本；规范语言为英文，见 [`README.md`](README.md)。文档规范遵循 Aprism 文档模式：英文为主、中文副本同步维护。

---

## 功能概览

Despotes 在 Minecraft 客户端进程内部运行一个小型控制服务器：

| 能力 | 实现方式 |
|---|---|
| **输入注入** | 按键、打字、移动、GUI 点击与世界交互直接注入游戏自身的输入处理器。完全不触碰操作系统键鼠，无需窗口焦点。 |
| **视角控制** | 视角旋转直接作用于玩家本体，绕过鼠标管线。 |
| **画面截取** | 在渲染线程直接抓取帧缓冲，即使游戏窗口失焦（平台允许时甚至最小化）也能截图。可通过 HTTP 获取或保存到磁盘。 |
| **外源控制配置** | 游戏目录下的 `despotes.json` 声明启用哪些控制源：内置 HTTP 服务器、stdin 命令行、文件投放目录，以及插件式来源。 |
| **操作可视化** | 每一条外源操作都会显示在游戏内覆盖层（overlay），并追加写入 `despotes-oplog.jsonl`，外部控制源在做什么始终可见。 |

## 兼容性（v26.12 线）

| 平台 | 分支 | Minecraft 版本 |
|---|---|---|
| Minecraft 原生（premain javaagent + Mixin） | `native` | 1.20.1, 1.21.1, 1.21.4, 1.21.10, 26.1.2, 26.2 |
| Aprism Native（`.aje`） | `aprism` | 26.2 |
| Fabric | `fabric` | 1.20.1, 1.21.1, 1.21.4, 1.21.10, 26.1.2, 26.2 |
| NeoForge | `neoforge` | 1.21.1, 1.21.4, 1.21.10, 26.1.2, 26.2 |
| Forge（≤ 1.21.1） | `forge` | 1.20.1, 1.21.1 |

每个加载器一个分支（分支规范参考 Aprism 文档 §12）。每个分支携带一份加载器无关控制核心 `common/` 副本，以及加载器专属入口与版本子工程。

## 快速开始

1. 从 [Releases](../../releases) 下载对应加载器与 Minecraft 版本的产物，放入对应模组文件夹（Fabric/NeoForge/Forge/Aprism 为 `mods/`；原生加载器以 `-javaagent:Despotes-...jar` 启动）。
2. 首次启动游戏，游戏目录会生成 `despotes.json`。
3. 控制游戏，例如：

```bash
curl -X POST http://127.0.0.1:25585/despotes/v1/actions \
  -H "Content-Type: application/json" \
  -d '{"type":"key","keys":["key.keyboard.w"],"holdTicks":20}'
```

完整 HTTP/CLI 协议见 [docs/zh/02-控制协议.md](docs/zh/02-控制协议.md)，配置说明见 [docs/zh/03-配置.md](docs/zh/03-配置.md)。

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->

## 安全模型

- 所有传输默认绑定 `127.0.0.1`，仅接受回环来源。
- 远程控制来源必须由用户显式添加到 `security.allowSources`。
- Despotes 从不向机器外部发送任何数据，它只监听。

## 许可证

Apache License 2.0 —— 见 [LICENSE](LICENSE)。

## 版本管理

Despotes 遵循 Aprism 版本方案 `v<年份>.<小版本>[-Alpha.<n>]`（见 [docs/zh/04-版本管理.md](docs/zh/04-版本管理.md)）。Alpha 构建以 GitHub **Pre-Release** 发布；小版本正式版（`v26.0`）以 GitHub **Release** 发布。
