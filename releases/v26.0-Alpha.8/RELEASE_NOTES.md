# Despotes v26.0-Alpha.8 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition. External sources drive the
> game without ever touching your keyboard or mouse.

## Highlights

- **Coverage expansion (runtime-verified this cycle)**:
  - `Despotes-v26.0-Alpha.8-fabric-1.21.4.jar` — Fabric line now verified on 1.21.4
    (status / GUI clicks / screenshots exercised via MDL while unfocused).
- **Native line expanded**: `native-1.21.1` and `native-1.20.1` join `native-26.2`
  (build-verified javaagent artifacts for the legacy official-mapping range; runtime
  verification on MDL is limited by the launcher's `--java-path` support for these
  versions, same constraint as the Forge line).
- **Eleven artifacts in one cycle** across five loader lines:
  Fabric (26.2 / 1.21.4 / 1.21.1 / 1.20.1), NeoForge (26.2 / 1.21.1),
  Native (26.2 / 1.21.1 / 1.20.1), Forge (1.21.1), Aprism (26.2 .aje).

## Compatibility

| Loader | Minecraft versions | Status |
|---|---|---|
| Fabric | 26.2, 1.21.4, 1.21.1, 1.20.1 | runtime-verified |
| NeoForge | 26.2, 1.21.1 | runtime-verified |
| Native (javaagent) | 26.2, 1.21.1, 1.20.1 | 26.2 runtime-verified; legacy build-verified |
| Forge | 1.21.1 | build-verified |
| Aprism | 26.1 – 26.2 | build-verified (needs Aprism loader at runtime) |

## Assets

- `Despotes-v26.0-Alpha.8-fabric-26.2.jar`
- `Despotes-v26.0-Alpha.8-fabric-1.21.4.jar`
- `Despotes-v26.0-Alpha.8-fabric-1.21.1.jar`
- `Despotes-v26.0-Alpha.8-fabric-1.20.1.jar`
- `Despotes-v26.0-Alpha.8-neoforge-26.2.jar`
- `Despotes-v26.0-Alpha.8-neoforge-1.21.1.jar`
- `Despotes-v26.0-Alpha.8-native-26.2.jar`
- `Despotes-v26.0-Alpha.8-native-1.21.1.jar`
- `Despotes-v26.0-Alpha.8-native-1.20.1.jar`
- `Despotes-v26.0-Alpha.8-forge-1.21.1.jar` (build-verified)
- `Despotes-v26.0-Alpha.8-aprism-26.2.aje` (build-verified)

---

# Despotes v26.0-Alpha.8（预发布）中文说明

- **覆盖扩展**：Fabric 1.21.4 运行时验证通过；原生 javaagent 线扩展至 1.21.1 / 1.20.1。
- **本周期发布十一个构件**，五条加载器线覆盖 1.20.1 – 26.2。
