# Despotes v26.10-Alpha.1 — Version Support Expansion / 版本支持扩展

**English**

The v26 line extends per the BC skill version spec (`v{Year}.{Major}`, Major 0-∞, valid through 2026-12-31). This cycle adds **six new loader-version lines**, expanding the matrix from 14 to 20 artifacts and closing the long-standing roadmap commitment of broader 1.20.x-26.x coverage.

## New version lines (6)

| Line | MC | Loader | Notes |
|---|---|---|---|
| fabric-26.1.2 | 26.1.2 | Fabric | fabric-api 0.155.2+26.1.2, loader 0.19.2 |
| native-26.1.2 | 26.1.2 | Native javaagent | Compiles against the unobfuscated official 26.1.2 client jar |
| neoforge-26.1.2 | 26.1.2 | NeoForge | neo 26.1.2.94 — NeoForm first-pass decompile still building at release time |
| fabric-1.21.10 | 1.21.10 | Fabric | fabric-api 0.138.4+1.21.10, loom 1.11.7, Gradle 8.14, Java 21 |
| native-1.21.10 | 1.21.10 | Native javaagent | Uses fabric-1.21.10's loom-cache merged jar |
| neoforge-1.21.10 | 1.21.10 | NeoForge | neo 21.10.64 — pending NeoForm build |

## Platform adaptations for the new ranges

The 26.1/1.21.9+ API surface differs from both 26.2 and older 1.21.x:

- `Minecraft.screen` is a public field on these versions (`gui.screen()` arrived in 26.2) — screen accessors patched accordingly.
- `Screen.keyPressed(KeyEvent)` / `mouseClicked(MouseButtonEvent, boolean)` record-style input events — key/mouse injection wrapped.
- `ChatScreen(String, boolean)` constructor.
- Screenshots use the callback-style `Screenshot.takeScreenshot(RenderTarget, Consumer<NativeImage>)` with a settled-guard.
- HUD overlay renders through fabric-api's **HudElementRegistry** (no Hud mixin; HudMixin dropped for these lines).
- 1.21.10 specifics: `Inventory.getSelectedSlot()` / `getNonEquipmentItems()` replace direct field access; `ResourceLocation` naming; `isWindowActive()` focus probe; access widener namespace `named`; `java>=21` dependency.

## Verification

Runtime tested on MDL instances:
- **openlumin-fabric-26.1.2** (MC 26.1.2): boot ✓, ping/self queries ✓, click-driven world entry (title → Select World → in-world) ✓, screenshot via new callback path (854x480) ✓, chat action submitted ✓. Known limitation: `/give` feedback and event-stream capture silent on this line (fabric-api 0.155.2 event behavior) — recorded for follow-up.
- **openlumin-fabric-1.21.10** (MC 1.21.10): boot ✓, ping ✓, fps telemetry ✓.

All **18 artifacts** built green (neoforge-26.1.2 / neoforge-1.21.10 pending their first NeoForm decompile-recompile pass; to be attached at the stable cut).

## Assets (18)

- Despotes-v26.10-Alpha.1-fabric-26.2.jar
- Despotes-v26.10-Alpha.1-fabric-26.1.2.jar
- Despotes-fabric-1.21.10-v26.10-Alpha.1-fabric-1.21.10.jar
- Despotes-fabric-1.21.4-v26.10-Alpha.1-fabric-1.21.4.jar
- Despotes-fabric-1.21.1-v26.10-Alpha.1-fabric-1.21.1.jar
- Despotes-fabric-1.20.1-v26.10-Alpha.1-fabric-1.20.1.jar
- Despotes-v26.10-Alpha.1-native-26.2.jar
- Despotes-v26.10-Alpha.1-native-26.1.2.jar
- Despotes-v26.10-Alpha.1-native-1.21.10.jar
- Despotes-v26.10-Alpha.1-native-1.21.4.jar
- Despotes-v26.10-Alpha.1-native-1.21.1.jar
- Despotes-v26.10-Alpha.1-native-1.20.1.jar
- Despotes-v26.10-Alpha.1-neoforge-26.2.jar
- Despotes-v26.10-Alpha.1-neoforge-1.21.4.jar
- Despotes-v26.10-Alpha.1-neoforge-1.21.1.jar
- Despotes-v26.10-Alpha.1-forge-1.21.1.jar
- Despotes-v26.10-Alpha.1-forge-1.20.1.jar
- Despotes-v26.10-Alpha.1-aprism-26.2.jar

---

**中文**

按 BC 技能版本规范（`v{Year}.{Major}`，Major 0-∞，2026-12-31 前有效）延续 v26 线。本周期新增 **6 条加载器-版本线**，构件矩阵从 14 扩展到 20，兑现路线图中"1.20.x-26.x 更广覆盖"的承诺。

## 新版本线（6 条）

26.1.2（fabric/native/neoforge）+ 1.21.10（fabric/native/neoforge）。neoforge 两条线因 NeoForm 首次反编译重编译耗时较长，发布时仍在构建中，将在 stable 时补挂。

## 新区间平台适配

- `Minecraft.screen` 公开字段（`gui.screen()` 是 26.2 才有）
- KeyEvent/MouseButtonEvent 记录式输入事件包装
- 回调式 `Screenshot.takeScreenshot` + settled 守卫
- HUD 覆盖层走 fabric-api **HudElementRegistry**（去 HudMixin）
- 1.21.10 特有：Inventory 公开访问器、ResourceLocation 命名、AW namespace=named、java>=21

## 验证

- openlumin-fabric-26.1.2：启动/ping/self/点击进世界/截图(854x480)/chat 全链路 ✓；已知限制：/give 反馈与事件流静默（fabric-api 0.155.2 行为），已记录跟进
- openlumin-fabric-1.21.10：启动/ping/fps ✓
- 全部 18 构件构建绿
