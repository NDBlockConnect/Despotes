# Despotes v26.1-Alpha.1 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition.
> First alpha of the v26.1 line.

## Fixes & features (issue list)

1. **Look actually applies** — the v26.0 camera write was silently dropped; rewritten with a
   dedicated frame-driven animator that writes the player rotation every rendered frame.
2. **Smooth look** — frame-driven ease-in-out over a millisecond duration (`lookSmoothMs`,
   per-command `durationMs` override). Samples confirm progressive yaw (e.g. -133 → -76 → 0).
3. **Data-level world perception** — new queries `world`, `blocks`, `entities`, `target`
   read straight from client state (biome/time/difficulty/seed, block snapshot, nearest
   entities, crosshair target with distance), independent of the framebuffer.
4. **Function keys** — new `function` action: `toggle-perspective` (F5), `toggle-debug`
   (F3), `toggle-fullscreen`, `toggle-hide-gui` (F1), `open-inventory`; direct semantic calls,
   version-tolerant.
5. **Screen widget coordinates** restored across versions (rect read per-version).
6. **Launch focus policy** — `window.grabFocusOnStart` (default `false`): the game no longer
   steals OS focus on start; `focus.keepReleasedWhileUnfocused` keeps the cursor released
   every tick while unfocused so the game never locks/steals your mouse while you work
   elsewhere.
7. **File-drop replay fix** — `fileDrop` consumed the same file repeatedly (overwriting
   later commands, the v26.0 "look looks locked" symptom); files are now consumed once and
   re-consumed only when their fingerprint changes.

## Verification (tested before release, per policy)

- Fabric 26.2 / 1.21.1 / 1.20.1: world entry via clicks, look smooth+instant sampled, world/
  target/blocks/entities queries, function keys, filedrop single-consume regression — all
  exercised in MDL while unfocused.
- NeoForge 26.2 / 1.21.1, Forge 1.21.1, native 26.2/1.21.1/1.20.1, aprism .aje: build-verified.

## Assets (11)

- `Despotes-v26.1-Alpha.1-fabric-26.2.jar`
- `Despotes-v26.1-Alpha.1-fabric-1.21.4.jar`
- `Despotes-v26.1-Alpha.1-fabric-1.21.1.jar`
- `Despotes-v26.1-Alpha.1-fabric-1.20.1.jar`
- `Despotes-v26.1-Alpha.1-neoforge-26.2.jar`
- `Despotes-v26.1-Alpha.1-neoforge-1.21.1.jar`
- `Despotes-v26.1-Alpha.1-forge-1.21.1.jar`
- `Despotes-v26.1-Alpha.1-native-26.2.jar`
- `Despotes-v26.1-Alpha.1-native-1.21.1.jar`
- `Despotes-v26.1-Alpha.1-native-1.20.1.jar`
- `Despotes-v26.1-Alpha.1-aprism-26.2.aje`

---

# Despotes v26.1-Alpha.1（预发布）中文说明

- **视角修复**：重写为帧驱动动画器，look 真正生效且平滑（ease-in-out，`durationMs` 可控）。
- **数据层感知**：新增 `world`/`blocks`/`entities`/`target` 查询，直接读客户端状态。
- **功能键**：`function` 动作（F5 切视角/F3 调试/全屏/隐藏GUI/背包），跨版本容错。
- **启动焦点**：`window.grabFocusOnStart=false` 默认不抢焦点；失焦时每 tick 保持释放鼠标，
  不再锁定/抢占用户鼠标。
- **filedrop 重放缺陷修复**：文件仅消费一次，指纹变化才重新消费（解决"look 被锁死"）。
- 实测：Fabric 26.2/1.21.1/1.20.1 全项通过；其余加载器构建验证。
