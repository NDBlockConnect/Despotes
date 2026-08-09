# Despotes v26.0-Alpha.5 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition. External sources drive the
> game without ever touching your keyboard or mouse.

## Highlights

- **New loader line: Minecraft Native (vanilla, javaagent).**
  `Despotes-v26.0-Alpha.5-native-26.2.jar` runs on a plain vanilla client (no loader):
  attach it with `-javaagent:Despotes-v26.0-Alpha.5-native-26.2.jar` (MDL: launch with
  `--java-path <wrapper>` where the wrapper adds the `-javaagent` flag). It compiles against
  the official-named 26.2 client jar and drives the shared control core from a 20 Hz poller
  hopping onto the client thread — no bytecode weaving, no loader required. Verified in-game
  on vanilla 26.2: status, GUI clicks, screenshots (unfocused-safe), key/move/look/mouse.
- **Forge line now build-verified end-to-end**: `Despotes-v26.0-Alpha.5-forge-1.21.1.jar`
  (Forge 52.1.16, official mappings, Java 21). Runtime verification remains blocked by
  launcher/tooling in this environment (see Alpha.4 notes); shipping build-verified.
- **NeoForge 26.2** and the **Fabric line (26.2 / 1.21.1 / 1.20.1)** re-published under the
  Alpha.5 stamp; unchanged feature set from Alpha.3/4 (all runtime-verified previously).
- **NeoForge 1.21.1** source set is complete (committed on the `neoforge` branch); its
  artifact remains deferred to Alpha.6 because this environment cannot finish the NeoGradle
  21.1 recompile pipeline (WAF-blocked Maven plus incomplete incremental recompile).
- **Aprism Native line**: the local Aprism loader is itself pre-release (v26.0-Alpha.7,
  Phase0 internal) and does not yet ship a consumable `aprism-api` artifact, so the `aprism`
  branch carries the `.aje` manifest/module sources (per Aprism docs §2/§8) and its artifact
  is deferred until Aprism publishes a stable API. Tracked for Alpha.6+.

## Compatibility

| Loader | Minecraft versions | Status |
|---|---|---|
| Native (javaagent) | 26.2 | runtime-verified (this release) |
| Fabric | 26.2, 1.21.1, 1.20.1 | runtime-verified (Alpha.3/4) |
| NeoForge | 26.2 | runtime-verified (Alpha.3) |
| Forge | 1.21.1 | build-verified |
| Aprism | 26.1 – 26.2 | sources only (deferred) |

## Assets

- `Despotes-v26.0-Alpha.5-native-26.2.jar`
- `Despotes-v26.0-Alpha.5-fabric-26.2.jar`
- `Despotes-v26.0-Alpha.5-fabric-1.21.1.jar`
- `Despotes-v26.0-Alpha.5-fabric-1.20.1.jar`
- `Despotes-v26.0-Alpha.5-neoforge-26.2.jar`
- `Despotes-v26.0-Alpha.5-forge-1.21.1.jar` (build-verified)

---

# Despotes v26.0-Alpha.5（预发布）中文说明

- **新增加载器线：Minecraft 原生（vanilla，javaagent）。** 无需任何加载器，以
  `-javaagent:` 方式附加即可在原版 26.2 客户端内开启控制通道；已实测 status、GUI 点击、
  失焦截图、按键/移动/视角/鼠标控制。
- **Forge 线（1.21.1）** 构件构建验证通过；运行时验证受本环境启动器/工具链限制顺延。
- **NeoForge 26.2 与 Fabric 三版本** 随本版本盖印发行（功能与此前一致，均已实测）。
- **NeoForge 1.21.1** 源码完成、构件顺延至 Alpha.6（本环境无法完成 NeoGradle 重编译管线）。
- **Aprism 线** 因 Aprism 加载器自身尚处内部 Alpha、未发布可消费 API，本周期仅随附源码与
  `.aje` 清单，构件顺延。
