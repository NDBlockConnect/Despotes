# Despotes v26.3-Alpha.3 — Craft Action / 合成动作

**English**

Adds a `craft` action that places materials into the crafting grid and extracts the result.

## New action: `craft`

Three modes:

### `recipe` — place items and extract

```json
{"type":"craft","mode":"recipe","grid":{"1":36,"2":36,"3":36,"4":36}}
```

- `grid`: maps crafting grid slot (1-4 for 2x2, 1-9 for 3x3) to the source inventory slot
- Right-clicks the source slot to pick up 1 item, then left-clicks the grid slot to place it
- After all placements, shift-clicks the result slot (slot 0) to auto-transfer the crafted item to inventory

### `result` — extract pre-filled result

```json
{"type":"craft","mode":"result"}
```

Shift-clicks the result slot when the grid is already filled (e.g. by the recipe book).

### `autocraft` — recipe book auto-fill

Equivalent to `result` mode — assumes the recipe book has filled the grid.

## Implementation

- Uses `slotClick` with right-click (button=1) to pick up single items from source stacks
- Uses `quick_move` (shift-click) on result slot 0 to auto-transfer crafted items to inventory
- Works with both 2x2 (inventory) and 3x3 (crafting table) crafting grids

## Verification (tested before release)

- Fabric 26.2 (MC 26.2) runtime:
  - Gave 8 oak_planks, opened inventory (2x2 grid)
  - `craft` recipe mode with grid 1-4 all from slot 36 → crafting_table produced ✓
  - Result auto-transferred to inventory via shift-click ✓

## Assets (12 of 14; forge-1.21.1 pending — GC thrashing)

---

**中文**

新增 `craft` 合成动作，放置材料到合成网格并提取产物。

## 验证

在 fabric-26.2（MC 26.2）上实测：给 8 个橡木木板，打开物品栏，执行 craft 后成功合成工作台并自动放入物品栏 ✓
