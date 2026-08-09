# Despotes v26.0-Alpha.7 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition. External sources drive the
> game without ever touching your keyboard or mouse.

## Highlights

- **New loader line: Aprism Native (.aje).** `Despotes-v26.0-Alpha.7-aprism-26.2.aje` targets
  the Aprism JE loader (premain javaagent + loader core): it implements `IAprismMod`, carries
  an `aprism.manifest.json` at the pack root per Aprism doc §2/§8, and bundles the compiled
  mod jar as `despotes.jar`. Compiled against the local Aprism `aprism-api` classes; runtime
  requires an Aprism loader ≥ 26.0-Alpha.1 (the Aprism project itself is still pre-release).
- **Full eight-artifact matrix** now ships in one cycle: native 26.2, Fabric 26.2/1.21.1/
  1.20.1, NeoForge 26.2/1.21.1, Forge 1.21.1, Aprism 26.2 (.aje).

## Compatibility

| Loader | Minecraft versions | Status |
|---|---|---|
| Native (javaagent) | 26.2 | runtime-verified (Alpha.5) |
| Fabric | 26.2, 1.21.1, 1.20.1 | runtime-verified |
| NeoForge | 26.2, 1.21.1 | runtime-verified (Alpha.6) |
| Forge | 1.21.1 | build-verified |
| Aprism | 26.1 – 26.2 | build-verified (needs Aprism loader at runtime) |

## Assets

- `Despotes-v26.0-Alpha.7-aprism-26.2.aje`
- `Despotes-v26.0-Alpha.7-native-26.2.jar`
- `Despotes-v26.0-Alpha.7-fabric-26.2.jar`
- `Despotes-v26.0-Alpha.7-fabric-1.21.1.jar`
- `Despotes-v26.0-Alpha.7-fabric-1.20.1.jar`
- `Despotes-v26.0-Alpha.7-neoforge-26.2.jar`
- `Despotes-v26.0-Alpha.7-neoforge-1.21.1.jar`
- `Despotes-v26.0-Alpha.7-forge-1.21.1.jar` (build-verified)

---

# Despotes v26.0-Alpha.7（预发布）中文说明

- **新增加载器线：Aprism Native（.aje）。** 实现 `IAprismMod` 入口，包根含
  `aprism.manifest.json`（按 Aprism 文档规范），运行时需 Aprism 加载器 ≥ 26.0-Alpha.1。
- **本周期一次性发布八个构件**，五条加载器线齐备：原生 javaagent、Fabric 三版本、
  NeoForge 两版本、Forge、Aprism。
