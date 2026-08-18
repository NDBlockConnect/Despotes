# Despotes v26.7 (Stable Release)

> In-process local control channel for Minecraft: Java Edition.

Stable release of the v26.7 line — Building and Block Operations.

## What's new since v26.6

### Building and Block Operations

- **`place-block`** action: look at target coordinates and use item to place a block
- **`dig`** action: look at target coordinates and attack to break a block
- **`fill`** action: fill a cuboid region using `/fill` command

## Verification (fabric-26.2, MC 26.2)

- fill: correctly validated (rejected with NOT_IN_GAME when not in world) ✓
- WS port 25586: listening ✓

## Assets (13 of 14; aprism pending)

---

**中文**

v26.7 正式版 — 建筑与方块操作。新增 `place-block`/`dig`/`fill` 动作。
