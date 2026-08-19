# Despotes v26.5-Alpha.1 — Pathfinding and Navigation / 寻路与导航

**English**

Adds `goto`, `follow`, and `stop-nav` actions for autonomous navigation.

## New actions

### `goto` — navigate to coordinate or entity
```json
{"type":"goto","x":100,"y":64,"z":200,"stopDistance":2.0}
{"type":"goto","uuid":"12345678-...","stopDistance":3.0}
```

### `follow` — follow an entity
```json
{"type":"follow","uuid":"12345678-...","stopDistance":3.0}
```

### `stop-nav` — stop navigation
```json
{"type":"stop-nav"}
```

## Implementation

- `PathNavigator` class: runs on client thread each tick, drives movement via `setMovement`
- Greedy best-first pathfinding: looks at target, sets yaw via `LookSmoother`, moves forward
- Obstacle detection: probes blocks ahead using `probeBlocks`, triggers jump on collision
- Sprint mode when target is far (>6 blocks)
- Timeout after 600 ticks (30s) by default
- Cancels when player reaches target or manually moves

## Verification (fabric-26.2, MC 26.2)

- goto started: True ✓
- Navigation ran and moved player ✓
- stop-nav correctly stopped navigation ✓

## Assets (12 of 13; forge-1.21.1 pending)

---

**中文**

新增 `goto`（导航到坐标/实体）、`follow`（跟随实体）、`stop-nav`（停止导航）动作。基于每 tick 的贪心寻路和障碍检测。
