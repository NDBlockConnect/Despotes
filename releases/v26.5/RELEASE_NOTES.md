# Despotes v26.5 (Stable Release)

> In-process local control channel for Minecraft: Java Edition.

Stable release of the v26.5 line — Pathfinding and Navigation.

## What's new since v26.4

### Pathfinding and Navigation

- **`goto`** action: navigate to a coordinate or entity UUID with configurable stop distance
- **`follow`** action: continuously follow an entity by UUID, updating target position each tick
- **`stop-nav`** action: stop active navigation

### Implementation

- `PathNavigator` class runs on the client thread each tick
- Greedy best-first walk: sets yaw towards target, moves forward via `setMovement`
- Obstacle detection via `probeBlocks` — triggers jump when blocked
- Sprint mode when target > 6 blocks away
- 30s timeout, auto-cancel on reach or manual camera move

## Verification (fabric-26.2, MC 26.2)

- goto started=True, navigation moved player, stop-nav stopped ✓

## Assets (12 of 13; forge-1.21.1 pending)

---

**中文**

v26.5 正式版 — 寻路与导航。新增 `goto`/`follow`/`stop-nav` 动作，基于每 tick 的贪心寻路与障碍检测。
