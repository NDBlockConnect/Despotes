# Despotes v26.10 — Stable Release (Version Support Expansion)

The v26.10 series completes the **version support expansion** commitment: the artifact matrix grows from 14 to **20 artifacts**, adding full **26.1.2** and **1.21.10** lines across fabric/native/neoforge. Five loader lines: **native** (javaagent, 1.20.1 → 26.2), **fabric** (1.20.1 → 26.2), **neoforge** (1.21.1 → 26.2), **forge** (1.20.1 / 1.21.1), **aprism** (26.2, `.aje`).

## What's new since v26.9

### New version lines (6)

| Line | MC | Loader | Build notes |
|---|---|---|---|
| fabric-26.1.2 | 26.1.2 | Fabric | fabric-api 0.155.2+26.1.2, loader 0.19.2 |
| native-26.1.2 | 26.1.2 | Native javaagent | Official-named unobfuscated 26.1.2 client jar |
| neoforge-26.1.2 | 26.1.2 | NeoForge | neo 26.1.2.94 |
| fabric-1.21.10 | 1.21.10 | Fabric | fabric-api 0.138.4+1.21.10, loom 1.11.7, Gradle 8.14, Java 21 |
| native-1.21.10 | 1.21.10 | Native javaagent | Uses fabric-1.21.10 loom-cache merged jar |
| neoforge-1.21.10 | 1.21.10 | NeoForge | neo 21.10.64; includes a NeoForm decompile-defect patch hook |

### Platform adaptations for the 26.1 / 1.21.9+ API surface

- `Minecraft.screen` public field (`gui.screen()` is 26.2-only)
- `Screen.keyPressed(KeyEvent)` / `mouseClicked(MouseButtonEvent, boolean)` record-style input
- `ChatScreen(String, boolean)` constructor
- Callback-style `Screenshot.takeScreenshot(RenderTarget, Consumer<NativeImage>)` with settled guard
- HUD overlay via fabric-api **HudElementRegistry** (no Hud mixin on new lines)
- 1.21.10: `WorldVersion.id()`, `Inventory.getSelectedSlot()/getNonEquipmentItems()`, `ResourceLocation` naming, `isWindowActive()`, access widener namespace `named`, `java>=21`

### neoforge-1.21.10 NeoForm decompile patch

Vineflower fails on `EntitySectionStorage.getEntities` in 1.21.10, emitting an illegal `$VF: Couldn't be decompiled` lambda placeholder. The build now patches the transformed source automatically after `neoFormTransformSource` (idempotent doLast hook in build.gradle).

## Verification

- Runtime tested on MDL instances:
  - **openlumin-fabric-26.1.2** (MC 26.1.2): boot, ping/self queries, click-driven world entry, screenshot (854x480), chat action. Known limitation: `/give` feedback and event-stream capture silent on this line (fabric-api 0.155.2 event behavior).
  - **openlumin-fabric-1.21.10** (MC 1.21.10): boot, ping, fps telemetry.
  - **Vanilla 26.2 + Aprism dual-agent**: v26.10 native co-attached with Aprism v26.6 agent survives 4+ min past title screen, HTTP channel healthy (companion/pump mode) — confirming the Alpha.4 loader-mixing guard. **v26.1-native remains incompatible with loader co-attachment; use v26.2+.**
- All **20/20 artifacts** built green across native/fabric/neoforge/forge/aprism.

## Assets (20)

- Despotes-v26.10-fabric-26.2.jar / Despotes-v26.10-fabric-26.1.2.jar
- Despotes-fabric-1.21.10-v26.10-fabric-1.21.10.jar
- Despotes-fabric-1.21.4-v26.10-fabric-1.21.4.jar
- Despotes-fabric-1.21.1-v26.10-fabric-1.21.1.jar
- Despotes-fabric-1.20.1-v26.10-fabric-1.20.1.jar
- Despotes-v26.10-native-26.2.jar / Despotes-v26.10-native-26.1.2.jar
- Despotes-v26.10-native-1.21.10.jar
- Despotes-v26.10-native-1.21.4.jar / Despotes-v26.10-native-1.21.1.jar / Despotes-v26.10-native-1.20.1.jar
- Despotes-v26.10-neoforge-26.2.jar / Despotes-v26.10-neoforge-26.1.2.jar
- Despotes-v26.10-neoforge-1.21.10.jar
- Despotes-v26.10-neoforge-1.21.4.jar / Despotes-v26.10-neoforge-1.21.1.jar
- Despotes-v26.10-forge-1.21.1.jar / Despotes-v26.10-forge-1.20.1.jar
- Despotes-v26.10-aprism-26.2.jar

---

# Despotes v26.10（正式版 — 版本支持扩展）

构件矩阵从 14 扩展到 **20**，新增 **26.1.2** 与 **1.21.10** 全线（fabric/native/neoforge），兑现"1.20.x-26.x 广覆盖"承诺。

## 自 v26.9 以来

- 6 条新版本线（见英文表）
- 26.1 / 1.21.9+ API 面适配（screen 字段、KeyEvent/MouseButtonEvent、回调式截图、HudElementRegistry、Inventory 公开访问器等）
- neoforge-1.21.10 内置 NeoForm 反编译缺陷自动补丁钩子
- 验证：双实例运行时实测 + 双 agent（Aprism + v26.10 native）共存存活确认（v26.1-native 与 loader 共存不兼容，已由 v26.2+ guard 取代）
- **20/20 构件**构建绿

## 已知限制

- fabric-26.1.2 线：事件流捕获与 /give 反馈静默（fabric-api 0.155.2 行为）
- v26.1-native-26.2.jar 与任何 loader 共挂会崩溃——请使用 v26.2+ native 构件
