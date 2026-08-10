# Despotes v26.1-Alpha.5 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition.

## Highlights — feature extension

1. **Container query** — `POST /query {"type":"container"}` returns the open screen menu's
   slots (`{open, title, slots:[{slot,item,count}]}`), data-level inventory/container
   perception across all versions (reflective, version-tolerant).
2. **Hotbar action** — `POST /actions {"type":"hotbar","slot":0-8}` selects the hotbar slot
   via reflective `setSelectedSlot`/`selected` field (verified: selectedSlot updates).
3. **Fourteen artifacts**.

## Verification (tested before release)

- Fabric 26.2 runtime: hotbar selection verified (slot 5), container query returns open
  menu; regression of look/move/click/status from Alpha.1-4.
- Other loaders rebuild-only.

## Assets (14)

- `Despotes-v26.1-Alpha.5-fabric-26.2.jar`
- `Despotes-v26.1-Alpha.5-fabric-1.21.4.jar`
- `Despotes-v26.1-Alpha.5-fabric-1.21.1.jar`
- `Despotes-v26.1-Alpha.5-fabric-1.20.1.jar`
- `Despotes-v26.1-Alpha.5-neoforge-26.2.jar`
- `Despotes-v26.1-Alpha.5-neoforge-1.21.4.jar`
- `Despotes-v26.1-Alpha.5-neoforge-1.21.1.jar`
- `Despotes-v26.1-Alpha.5-forge-1.21.1.jar`
- `Despotes-v26.1-Alpha.5-forge-1.20.1.jar`
- `Despotes-v26.1-Alpha.5-native-26.2.jar`
- `Despotes-v26.1-Alpha.5-native-1.21.4.jar`
- `Despotes-v26.1-Alpha.5-native-1.21.1.jar`
- `Despotes-v26.1-Alpha.5-native-1.20.1.jar`
- `Despotes-v26.1-Alpha.5-aprism-26.2.aje`

---

# Despotes v26.1-Alpha.5（预发布）中文说明

- **容器查询**：`container` 查询返回打开菜单的槽位（slot/item/count），数据层感知，
  全版本反射容错。
- **快捷栏动作**：`hotbar` 动作选择快捷栏（反射 setSelectedSlot/selected 字段），实测生效。
- 本周期十四个构件。
- 实测：Fabric 26.2 运行时验证 hotbar/container；其余加载器回归/构建验证。
