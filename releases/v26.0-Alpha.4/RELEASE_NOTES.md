# Despotes v26.0-Alpha.4 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition. External sources drive the
> game without ever touching your keyboard or mouse.

## Highlights

- **New loader line: NeoForge legacy.** `Despotes-v26.0-Alpha.4-neoforge-1.21.1.jar` extends
  the NeoForge line to the 1.21.x official-mapping range (Java 21), with the same feature
  set as the 26.2 variant: HTTP/CLI/file-drop transports, key/move/look/click/use/mouse
  actions, screenshots, overlay + op-log visualization, and focus-safe mouse handling.
- **Fabric line unchanged from Alpha.3** (re-published with the v26.0-Alpha.4 version stamp
  for matrix completeness): 26.2 / 1.21.1 / 1.20.1 artifacts.
- **Forge line (1.21.1) — build-verified.** `Despotes-v26.0-Alpha.4-forge-1.21.1.jar`
  compiles and packages against Forge 52.1.16 (official mappings, Java 21). Note: in this
  development environment neither MDL's Forge bootstrap nor ForgeGradle `runClient` could
  complete a full game launch (launcher/tooling issues, unrelated to the mod's code), so the
  Forge artifact ships **build-verified** this cycle and will be runtime-verified in the
  next Alpha.

## Verification

All NeoForge 1.21.1 and Fabric artifacts were exercised in-game via MDL while the window was
unfocused: `status`, `screenshot` (saved to `despotes-shots/`), GUI `click`, `key` (incl. ESC
routing through open screens), `move`/`look`, `mouse` capture control, and the inventory
query. The overlay showed the external op log in-game.

## Compatibility

| Loader | Minecraft versions |
|---|---|
| Fabric | 26.2, 1.21.1, 1.20.1 (declared range 1.20 – 1.21.11) |
| NeoForge | 26.2 (Alpha.3), 1.21.1 (this release) |
| Forge | 1.21.1 (build-verified this cycle) |

## Assets

- `Despotes-v26.0-Alpha.4-fabric-26.2.jar`
- `Despotes-v26.0-Alpha.4-fabric-1.21.1.jar`
- `Despotes-v26.0-Alpha.4-fabric-1.20.1.jar`
- `Despotes-v26.0-Alpha.4-neoforge-1.21.1.jar`
- `Despotes-v26.0-Alpha.4-forge-1.21.1.jar` (build-verified)

---

# Despotes v26.0-Alpha.4（预发布）中文说明

- **新增加载器版本：NeoForge 1.21.1。** 将 NeoForge 线扩展到 1.21.x 官方映射区间（Java 21），
  功能与 26.2 版本一致：HTTP/CLI/文件投递通道、按键/移动/视角/点击/世界交互/鼠标动作、截图、
  覆盖层与操作日志可视化、失焦鼠标安全处理。
- **Fabric 线** 与 Alpha.3 相同，随本版本重新盖印发行（26.2 / 1.21.1 / 1.20.1）。
- **Forge 线（1.21.1）为构建验证版。** 构件基于 Forge 52.1.16 编译打包成功；本开发环境中
  MDL 的 Forge bootstrap 与 ForgeGradle runClient 均无法完成完整游戏启动（启动器/工具链问题，
  与模组代码无关），故 Forge 构件本周期随附为构建验证版，下一 Alpha 补运行时验证。
