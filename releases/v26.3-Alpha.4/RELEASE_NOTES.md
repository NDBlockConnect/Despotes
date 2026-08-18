# Despotes v26.3-Alpha.4 — Interact, Trade, Sort / 交互、交易、整理

**English**

Adds `interact`, `trade`, and `sort` actions for world and inventory manipulation.

## New actions

### `interact` — right-click block/entity

```json
{"type":"interact","target":"crosshair"}
{"type":"interact","target":"block","x":10,"y":64,"z":20}
{"type":"interact","target":"entity","uuid":"12345678-..."}
```

- `crosshair`: use item on whatever the crosshair targets
- `block`: look at block coordinates, then use item (opens doors, toggles levers)
- `entity`: look at entity by UUID, then use item (talks to villagers, rides minecarts)

### `trade` — villager trade query and execution

```json
{"type":"trade","mode":"query"}
{"type":"trade","mode":"execute","index":0}
```

- Query mode reads merchant offers (result item, uses/maxUses) reflectively from the open MerchantMenu
- Execute mode selects a trade by index and clicks the result slot

### `sort` — inventory auto-sort

```json
{"type":"sort"}
```

Shift-clicks all main inventory slots (9-35) to auto-route items.

## Verification (tested on fabric-26.2, MC 26.2)

- `interact` crosshair mode: dispatched ✓
- `sort` action: moved 27 slots ✓

## Assets (12 of 13; forge-1.21.1 pending — GC thrashing)

---

**中文**

新增 `interact`（右键交互方块/实体）、`trade`（村民交易查询与执行）、`sort`（物品栏自动整理）三个动作。
