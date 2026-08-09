# Despotes v26.0-Alpha.6 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition. External sources drive the
> game without ever touching your keyboard or mouse.

## Highlights

- **NeoForge 1.21.1 now runtime-verified.** `Despotes-v26.0-Alpha.6-neoforge-1.21.1.jar`
  completes the NeoForge line on the legacy official-mapping range (Java 21). Verified
  in-game via MDL while the window was unfocused: status, GUI clicks, screenshots, key
  injection (ESC routing through open screens), and the full action set. This closes the
  artifact deferred since Alpha.4.
- **All seven loader/version artifacts ship in one cycle**:
  native (vanilla javaagent) 26.2, Fabric 26.2 / 1.21.1 / 1.20.1, NeoForge 26.2 / 1.21.1,
  Forge 1.21.1 (build-verified; runtime blocked by launcher tooling in this environment).
- Runtime version stamp unified to `v26.0-Alpha.6` across all artifacts.

## Compatibility

| Loader | Minecraft versions | Status |
|---|---|---|
| Native (javaagent) | 26.2 | runtime-verified (Alpha.5) |
| Fabric | 26.2, 1.21.1, 1.20.1 | runtime-verified (Alpha.3+) |
| NeoForge | 26.2, 1.21.1 | runtime-verified |
| Forge | 1.21.1 | build-verified |
| Aprism | 26.1 – 26.2 | sources only (deferred until Aprism ships a stable API) |

## Assets

- `Despotes-v26.0-Alpha.6-native-26.2.jar`
- `Despotes-v26.0-Alpha.6-fabric-26.2.jar`
- `Despotes-v26.0-Alpha.6-fabric-1.21.1.jar`
- `Despotes-v26.0-Alpha.6-fabric-1.20.1.jar`
- `Despotes-v26.0-Alpha.6-neoforge-26.2.jar`
- `Despotes-v26.0-Alpha.6-neoforge-1.21.1.jar`
- `Despotes-v26.0-Alpha.6-forge-1.21.1.jar` (build-verified)

---

# Despotes v26.0-Alpha.6（预发布）中文说明

- **NeoForge 1.21.1 运行时验证通过。** 补齐自 Alpha.4 顺延的构件；已在 MDL 中失焦实测
  status、GUI 点击、截图、按键注入与完整动作集。
- **本周期一次性发布七个构件**：原生 javaagent 26.2、Fabric 三版本、NeoForge 两版本、
  Forge 1.21.1（构建验证版）。
- **Aprism 线** 仍因 Aprism 加载器自身未发布稳定 API 而顺延（源码已在仓库）。
