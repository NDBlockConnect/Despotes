# Despotes v26.9 — Stable Release (v26 Line Finale)

The v26.9 series completes the v26 line with redstone perception and three automation primitives. All 14 artifacts are built across the five loader lines: **native** (javaagent, 1.20.1 → 26.2), **fabric** (1.20.1 → 26.2), **neoforge** (1.21.1 → 26.2), **forge** (1.20.1 / 1.21.1), and **aprism** (26.2, `.aje`).

## What's new since v26.8

### `redstone` query — signal strength at a block position

```json
{"type":"redstone","x":-517,"y":72,"z":-87}
```

Returns the block at the position, the maximum incoming signal strength across all 6 faces, and a list of adjacent redstone components (redstone wire, levers, buttons, torches, repeaters, comparators). When no coordinates are given, falls back to the crosshair target block.

### `schedule` action — periodic command execution

```json
{"type":"schedule","op":"add","name":"heartbeat","periodTicks":100,"commands":[{"type":"ping"}]}
```

Named repeating command sequences ticked on the client thread; `status` reports per-schedule execution counts and next-run timing. Every protocol action works inside a schedule.

### `macro` action — record & replay action sequences

```json
{"type":"macro","op":"start-recording","name":"demo"}
{"type":"macro","op":"record-step","command":{"type":"look","mode":"absolute","yaw":90,"pitch":0}}
{"type":"macro","op":"stop-recording"}
{"type":"macro","op":"play","name":"demo"}
```

Steps replay 1 tick apart through the standard action path; full lifecycle management included (`stop`/`delete`/`status`).

### `condition` action — conditional branch execution

```json
{"type":"condition",
 "if":{"type":"status","field":"result.inGame","op":"exists"},
 "then":[{"type":"ping"}],
 "else":[{"type":"chat","text":"not in game"}]}
```

Runs an inner query, extracts a field via dot-path into the response envelope, compares with one of six operators (`exists` / `eq` / `ne` / `gt` / `lt` / `contains`), then executes the matching branch inline.

## Implementation notes

- `ScheduleManager` + `MacroRecorder`: new common-layer subsystems ticked from `Despotes.clientTick()`.
- `WorldProbes.redstone()`: reflective `Level.getSignal(pos, direction)` per face with graceful degradation.
- Aprism build path fixed: API resolved from the MDL-cached agent jar instead of a stale source checkout.
- Build environment restored: all subproject Gradle build files re-tracked after an over-aggressive cleanup had removed them.

## Verification

- Runtime smoke on fabric-26.2 (MC 26.2): schedule heartbeat executed 4× in 6 s at periodTicks=100; macro 2-step recording + replay completed; condition `exists` and `gt` matching correct with inline branch execution; redstone block readout and signal query working.
- Full regression of prior lines' features passes (status/self/world/ping).
- **All 14/14 artifacts** built green across native/fabric/neoforge/forge/aprism.

## Assets (14)

- Despotes-v26.9-fabric-26.2.jar
- Despotes-fabric-1.21.4-v26.9-fabric-1.21.4.jar
- Despotes-fabric-1.21.1-v26.9-fabric-1.21.1.jar
- Despotes-fabric-1.20.1-v26.9-fabric-1.20.1.jar
- Despotes-v26.9-neoforge-26.2.jar
- Despotes-v26.9-neoforge-1.21.4.jar
- Despotes-v26.9-neoforge-1.21.1.jar
- Despotes-v26.9-forge-1.21.1.jar
- Despotes-v26.9-forge-1.20.1.jar
- Despotes-v26.9-native-26.2.jar
- Despotes-v26.9-native-1.21.4.jar
- Despotes-v26.9-native-1.21.1.jar
- Despotes-v26.9-native-1.20.1.jar
- Despotes-v26.9-aprism-26.2.jar

---

# Despotes v26.9（正式版，v26 线收官）

v26 系列以红石感知与三个自动化原语收官。全部 14 个构件跨五条加载器线构建。

## 自 v26.8 以来的新功能

### `redstone` 查询 — 方块位置红石信号
返回方块 ID、6 面最大入信号强度、相邻红石元件列表；无坐标时回退准星目标。

### `schedule` 动作 — 周期命令执行
按名称注册重复命令序列（客户端线程驱动），所有协议动作均可在任务内使用；`status` 报告执行计数与下次运行时间。

### `macro` 动作 — 动作序列录制与回放
步骤间隔 1 tick 经标准动作路径回放；完整生命周期管理（stop/delete/status）。

### `condition` 动作 — 条件分支执行
运行内层查询 → 点路径提取响应字段 → 六种比较操作符 → 内联执行匹配分支。

## 验证

fabric-26.2 运行时实测全部通过（心跳 4 次/6 秒、宏两步回放、条件匹配、红石读取）；全量回归通过；**14/14 构件**构建绿。
