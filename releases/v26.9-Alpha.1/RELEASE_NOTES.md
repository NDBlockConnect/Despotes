# Despotes v26.9-Alpha.1 — Redstone & Automation / 红石与自动化

**English**

The final feature release of the v26 line adds redstone perception and three automation primitives (schedule, macro, condition) that let external controllers compose complex behaviors without polling.

## New query

### `redstone` — signal strength at a block position

```json
{"type":"redstone","x":-517,"y":72,"z":-87}
```

Returns the block at the position, the maximum incoming signal strength across all 6 faces, and a list of adjacent redstone components (redstone wire, levers, buttons, torches, repeaters, comparators). When no coordinates are given, falls back to the crosshair target block.

## New actions

### `schedule` — periodic command execution

```json
{"type":"schedule","op":"add","name":"heartbeat","periodTicks":100,"commands":[{"type":"ping"}]}
{"type":"schedule","op":"remove","name":"heartbeat"}
{"type":"schedule","op":"clear"}
{"type":"schedule","op":"status"}
```

Registers repeating command sequences that run on the client tick. Each schedule has a name, period (ticks), and command list; `status` reports per-schedule execution counts and next-run timing.

### `macro` — record & replay action sequences

```json
{"type":"macro","op":"start-recording","name":"demo"}
{"type":"macro","op":"record-step","command":{"type":"look","mode":"absolute","yaw":90,"pitch":0}}
{"type":"macro","op":"stop-recording"}
{"type":"macro","op":"play","name":"demo"}
{"type":"macro","op":"stop"} / {"type":"macro","op":"delete","name":"demo"} / {"type":"macro","op":"status"}
```

Records action steps (each replayed 1 tick apart) and plays them back on the client thread.

### `condition` — conditional branch execution

```json
{"type":"condition",
 "if":{"type":"status","field":"result.inGame","op":"exists"},
 "then":[{"type":"ping"}],
 "else":[{"type":"chat","text":"not in game"}]}
```

Runs an inner query (`status`/`self`/`world`/`threats`/`ping`/…), extracts a field via dot-path into the response envelope (`result.inGame`, `result.fps`, …), compares with one of six operators (`exists`, `eq`, `ne`, `gt`, `lt`, `contains`), then executes the matching branch's actions inline.

## Implementation notes

- `ScheduleManager` + `MacroRecorder`: new common-layer subsystems ticked from `Despotes.clientTick()`; both execute commands through the standard `Actions.execute()` path so every action works inside schedules/macros.
- `WorldProbes.redstone()`: reflective `Level.getSignal(pos, direction)` per face with graceful degradation when the method is absent.
- Aprism build path fixed: API classes now resolved from the MDL-cached agent jar (`Aprism-v26.4-JE-26.2.jar`) instead of the stale source checkout.

## Verification (tested before release)

- Fabric 26.2 (MC 26.2) runtime:
  - schedule: heartbeat added, executed 4× in 6 s at periodTicks=100 ✓
  - macro: 2-step recording saved, replay completed ✓
  - condition: `exists` matched=true → then-branch ran; `gt` numeric compare (fps>10) matched=true ✓
  - redstone: block readout (`minecraft:leaf_litter`), signal=0, adjacent scan ✓
- All **14/14 artifacts** built green across native/fabric/neoforge/forge/aprism lines.

## Assets (14)

- Despotes-v26.9-Alpha.1-fabric-26.2.jar
- Despotes-fabric-1.21.4-v26.9-Alpha.1-fabric-1.21.4.jar
- Despotes-fabric-1.21.1-v26.9-Alpha.1-fabric-1.21.1.jar
- Despotes-fabric-1.20.1-v26.9-Alpha.1-fabric-1.20.1.jar
- Despotes-v26.9-Alpha.1-neoforge-26.2.jar
- Despotes-v26.9-Alpha.1-neoforge-1.21.4.jar
- Despotes-v26.9-Alpha.1-neoforge-1.21.1.jar
- Despotes-v26.9-Alpha.1-forge-1.21.1.jar
- Despotes-v26.9-Alpha.1-forge-1.20.1.jar
- Despotes-v26.9-Alpha.1-native-26.2.jar
- Despotes-v26.9-Alpha.1-native-1.21.4.jar
- Despotes-v26.9-Alpha.1-native-1.21.1.jar
- Despotes-v26.9-Alpha.1-native-1.20.1.jar
- Despotes-v26.9-Alpha.1-aprism-26.2.jar

---

**中文**

v26 系列的最后一个功能版本，加入红石感知与三个自动化原语（定时、宏、条件），让外部控制方无需轮询即可组合复杂行为。

## 新查询

### `redstone` — 方块位置的红石信号

返回方块 ID、6 面最大入信号强度，以及相邻红石元件列表（红石线/拉杆/按钮/火把/中继器/比较器）。无坐标时回退到准星目标方块。

## 新动作

### `schedule` — 周期命令执行

按名称注册重复命令序列（周期为 tick），在客户端 tick 上执行；`status` 报告每个任务的执行次数与下次运行时间。

### `macro` — 动作序列录制与回放

录制动作步骤（每步间隔 1 tick 回放）并在客户端线程回放；支持 start-recording / record-step / stop-recording / play / stop / delete / status。

### `condition` — 条件分支执行

运行内层查询，通过点路径提取响应信封中的字段（如 `result.inGame`、`result.fps`），用六种操作符比较（exists/eq/ne/gt/lt/contains），然后内联执行匹配分支的动作。

## 实现说明

- `ScheduleManager` + `MacroRecorder`：新的 common 层子系统，从 `Despotes.clientTick()` 驱动，均通过标准 `Actions.execute()` 路径执行——所有动作都可用于任务/宏。
- `WorldProbes.redstone()`：反射调用 `Level.getSignal(pos, direction)`，方法缺失时优雅降级。
- Aprism 构建路径修复：API 类改从 MDL 缓存的 agent jar 解析（替代过期的源码检出路径）。

## 验证

fabric-26.2 运行时实测：schedule 心跳 6 秒执行 4 次 ✓；macro 两步录制回放完成 ✓；condition exists/gt 匹配正确 ✓；redstone 方块读取与信号查询 ✓。全部 **14/14 构件**构建通过。
