# Despotes v26.0 (Release)

> In-process local control channel for Minecraft: Java Edition. External sources drive the
> game without ever touching your keyboard or mouse.
> First stable release of the v26 line.

## What Despotes does

Despotes runs a control server inside the Minecraft client process. External controllers
(HTTP on `127.0.0.1:25585`, stdin CLI, or a file-drop directory — configurable via
`despotes.json` in the game directory) can:

- inject keys / chat / typing (routed through open screens, ESC closes menus),
- drive movement and camera (per-tick synthetic input; no OS key/mouse events),
- click GUIs and interact with the world (attack / use / place / drop / pick),
- capture the framebuffer from the render thread (works while the window is unfocused),
- and query game state (status / screen widgets / inventory).

Every externally sourced operation is visualized in-game (HUD overlay) and appended to
`despotes-oplog.jsonl`. The game never grabs your OS mouse while unfocused:
`focus.releaseMouseOnFocusLoss` (default on) releases the captured cursor on focus loss and
re-captures it on regain; vanilla pause-on-lost-focus is disabled while controlled.

## Loader matrix (branches)

| Loader / branch | Minecraft versions | Verification |
|---|---|---|
| `fabric` | 26.2, 1.21.4, 1.21.1, 1.20.1 | runtime-verified (MDL) |
| `neoforge` | 26.2, 1.21.1 | runtime-verified (MDL) |
| `native` (premain javaagent) | 26.2, 1.21.1, 1.20.1 | 26.2 runtime-verified |
| `forge` | 1.21.1 (≤1.21.4 line) | build-verified |
| `aprism` (.aje) | 26.1 – 26.2 | build-verified (needs Aprism loader) |

One branch per loader, per Aprism docs §12 branch specification. Common control core is
byte-identical across branches.

## Assets (eleven)

- `Despotes-v26.0-fabric-26.2.jar`
- `Despotes-v26.0-fabric-1.21.4.jar`
- `Despotes-v26.0-fabric-1.21.1.jar`
- `Despotes-v26.0-fabric-1.20.1.jar`
- `Despotes-v26.0-neoforge-26.2.jar`
- `Despotes-v26.0-neoforge-1.21.1.jar`
- `Despotes-v26.0-native-26.2.jar` (attach: `-javaagent:Despotes-v26.0-native-26.2.jar`)
- `Despotes-v26.0-native-1.21.1.jar`
- `Despotes-v26.0-native-1.20.1.jar`
- `Despotes-v26.0-forge-1.21.1.jar`
- `Despotes-v26.0-aprism-26.2.aje`

## Protocol

JSON over HTTP (`/despotes/v1/{actions,query,status,cancel,oplog,screenshot}`), one-JSON-per-line
on stdin, or JSON files dropped in `despotes-in/`. Full schema: `docs/02-Control-Protocol.md`.
Configuration: `docs/03-Configuration.md`. Versioning: `docs/04-Versioning.md`.

## Notes

- Loopback-only by default; non-loopback sources require `security.allowSources`.
- Protocol and `despotes.json` schema are monotonically growing within the v26 line.

---

# Despotes v26.0（正式版）中文说明

- **v26 线首个正式版**：进程内本地控制通道，外源（HTTP/CLI/文件投递，经游戏目录
  `despotes.json` 配置）可注入按键/聊天/移动/视角/GUI 点击/世界交互、失焦截图、状态查询；
  全部外源操作在游戏内覆盖层与 `despotes-oplog.jsonl` 可视化；失焦自动释放鼠标、禁用
  原版失焦暂停，绝不占用用户键鼠。
- **五条加载器线、十一个构件**：Fabric（26.2/1.21.4/1.21.1/1.20.1，实测）、
  NeoForge（26.2/1.21.1，实测）、原生 javaagent（26.2 实测，1.21.1/1.20.1 构建验证）、
  Forge 1.21.1（构建验证）、Aprism 26.2 .aje（构建验证）。
- 每加载器一条分支（依 Aprism 文档 §12 分支规范）；common 控制核心跨分支字节一致。
