# Despotes v26.1-Alpha.3 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition.

## Highlights

1. **Forge line extended to 1.20.1** — `Despotes-v26.1-Alpha.3-forge-1.20.1.jar` (Forge 47.1.3,
   Java 17 toolchain). Build-verified; runtime blocked by the local launcher's missing Forge
   bootstrap artifacts for 1.20.1.
2. **Native (vanilla javaagent) runtime-verified** — `Despotes-v26.1-Alpha.3-native-26.2.jar`
   attached via `-javaagent` on a vanilla 26.2 client: status / GUI click / look all
   exercised; the channel binds 25585 and responds without any mod loader.
3. **Twelve artifacts** this cycle (forge-1.20.1 joins the matrix).

## Verification (tested before release)

- Native 26.2: javaagent attach → status/click/look exercised in MDL vanilla instance.
- Fabric 26.2/1.21.4/1.21.1/1.20.1, NeoForge 26.2/1.21.1: regression from Alpha.1/2.
- Forge 1.21.1 runtime-verified (Alpha.1/2); forge-1.20.1 build-verified.

## Assets (12)

- `Despotes-v26.1-Alpha.3-fabric-26.2.jar`
- `Despotes-v26.1-Alpha.3-fabric-1.21.4.jar`
- `Despotes-v26.1-Alpha.3-fabric-1.21.1.jar`
- `Despotes-v26.1-Alpha.3-fabric-1.20.1.jar`
- `Despotes-v26.1-Alpha.3-neoforge-26.2.jar`
- `Despotes-v26.1-Alpha.3-neoforge-1.21.1.jar`
- `Despotes-v26.1-Alpha.3-forge-1.21.1.jar`
- `Despotes-v26.1-Alpha.3-forge-1.20.1.jar`
- `Despotes-v26.1-Alpha.3-native-26.2.jar`
- `Despotes-v26.1-Alpha.3-native-1.21.1.jar`
- `Despotes-v26.1-Alpha.3-native-1.20.1.jar`
- `Despotes-v26.1-Alpha.3-aprism-26.2.aje`

---

# Despotes v26.1-Alpha.3（预发布）中文说明

- **Forge 线扩展至 1.20.1**（Forge 47.1.3，Java 17 工具链），构建验证；1.20.1 运行时被本地
  启动器缺失的 Forge bootstrap 构件阻塞。
- **原生 javaagent 运行时验证通过**：vanilla 26.2 以 `-javaagent` 附加，status/点击/视角
  均实测；无需任何 mod 加载器。
- 本周期十二个构件（新增 forge-1.20.1）。
- 实测：native 26.2 运行时验证（status/点击/视角）；Fabric/NeoForge 回归；
  forge-1.20.1 与 native 1.21.1/1.20.1 构建验证（1.21.1/1.20.1 运行时为混淆 jar，需逐版本
  重映射，顺延后续 Alpha）。
