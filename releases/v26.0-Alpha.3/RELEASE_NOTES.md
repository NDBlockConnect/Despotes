# Despotes v26.0-Alpha.3 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition. External sources drive the
> game without ever touching your keyboard or mouse.

## Highlights

- **New loader line: NeoForge.** `Despotes-v26.0-Alpha.3-neoforge-26.2.jar` brings the full
  control channel to NeoForge on Minecraft 26.2 (official mappings), mirroring the Fabric
  implementation (tick/HUD hooks via NeoForge events, access via AccessTransformer).
- **Focus-safe mouse handling (all loaders).** Vanilla Minecraft never releases the captured
  cursor when its window loses OS focus, so the game kept locking your mouse while you worked
  elsewhere. Despotes now:
  - releases the captured mouse whenever the window loses focus
    (`focus.releaseMouseOnFocusLoss`, default on),
  - re-captures it automatically on focus regain (`focus.regrabMouseOnFocusGain`),
  - and can disable vanilla pause-on-lost-focus (`focus.preventPauseOnFocusLoss`, default on)
    so external control keeps working while the window is unfocused.
  Manual control is available via the new `mouse` action (`grab` / `release` / `status`).
- **Key injection now works with GUIs open on 26.x too.** When a screen is open, injected
  keyboard events are routed through the open screen (so `escape` closes menus, etc.).
- **New `inventory` query** returns hotbar ids, non-empty slots and the selected slot.
- **Status now reports `mouseCaptured`.**

## Fixes (Fabric legacy range)

- Legacy artifacts (1.20.x / 1.21.x) no longer ship an access widener. Private Minecraft
  members are reached via reflection instead, keeping the intermediary-mapped artifacts
  loadable across mapping namespaces (fixes startup crashes under official-namespace
  runtimes such as MDL's launcher environment).

## Compatibility

| Loader | Minecraft versions |
|---|---|
| Fabric | 26.2, 1.21.1, 1.20.1 (declared range 1.20 – 1.21.11) |
| NeoForge | 26.2 |

## Protocol additions (v1, monotonic)

- `POST /despotes/v1/actions` — `{"type":"mouse","op":"grab|release|status"}`
- `POST /despotes/v1/query` — `{"type":"inventory"}` → `{selectedSlot, hotbar[], slots[]}`
- `GET /despotes/v1/status` — adds `mouseCaptured`
- `despotes.json` — new `focus` section (see docs/03-Configuration.md)

## Assets

- `Despotes-v26.0-Alpha.3-fabric-26.2.jar`
- `Despotes-v26.0-Alpha.3-fabric-1.21.1.jar`
- `Despotes-v26.0-Alpha.3-fabric-1.20.1.jar`
- `Despotes-v26.0-Alpha.3-neoforge-26.2.jar`

All artifacts were verified in-game via MDL (MCDebugLauncher): status/screenshot/GUI
click/key/move/look/chat/mouse all exercised while the game window was unfocused.

---

# Despotes v26.0-Alpha.3（预发布）中文说明

- **新增加载器线：NeoForge。** `Despotes-v26.0-Alpha.3-neoforge-26.2.jar` 将完整控制通道带到
  NeoForge（Minecraft 26.2，官方映射），与 Fabric 实现对齐。
- **失焦鼠标处理（全加载器）。** 原版在窗口失去焦点时从不释放被捕获的鼠标，导致游戏一直锁定
  你的鼠标。Despotes 现在会在失焦时自动释放鼠标、回焦时自动恢复，并可禁用原版的"失焦暂停"，
  使外源控制在窗口失焦时持续生效；另新增 `mouse` 动作可手动抓取/释放/查询。
- **26.x 有界面时的按键注入修复**：键盘事件会路由到当前打开的界面（ESC 可关闭菜单等）。
- **新增 `inventory` 查询** 与状态中的 `mouseCaptured` 字段。
- **Fabric 旧版本修复**：旧版构件不再附带 access widener，改用反射访问私有成员，避免在官方
  命名空间运行时（如 MDL 启动环境）启动崩溃。
