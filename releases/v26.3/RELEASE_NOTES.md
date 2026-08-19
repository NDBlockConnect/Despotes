# Despotes v26.3 (Stable Release)

> In-process local control channel for Minecraft: Java Edition.

Stable release of the v26.3 line, following Alpha.1–Alpha.9.

## What's new since v26.2

### Inventory and World Interaction

1. **Alpha.1 — inventory-action**: slot manipulation in open container menus (moveSlot, quickMove, drop, split, swap, pickup)
2. **Alpha.1 — equip**: armor equip/unequip (helmet/chestplate/leggings/boots)
3. **Alpha.2 — recipe**: recipe book query (known + highlighted recipes, RecipeManager fallback)
4. **Alpha.3 — craft**: place items in crafting grid (2x2/3x3), extract result via shift-click
5. **Alpha.4 — interact**: right-click block/entity (doors, levers, villagers) with coordinate/UUID targeting
6. **Alpha.4 — trade**: villager trade query (offers) and execution (selectTrade + result click)
7. **Alpha.4 — sort**: inventory auto-sort via shift-click all main slots

### Bug fixes

- `native-1.21.4/build.gradle`: updated Loom cache jar paths to current workspace
- `SecurityGate.isQuery()`: expanded inline query routing to include all read-only query types

## Implemented actions (v26.3 full set)

key, type, chat, move, look (delta/absolute/lookat), function, click, use (attack/useItem/placeBlock/drop/pickBlock), mouse, screenshot, status, screen, inventory, self, threats, world, blocks, entities, target, container, recipe, inventory-action, craft, interact, trade, sort, equip, hotbar, respawn, ping, ai, pending, config-reload

## Verification (tested before release)

- Fabric 26.2 (MC 26.2) runtime: full regression passed
  - status/self/world/recipe(1568)/interact/sort(27 moved)/ping(tickCount)/craft(extracted) ✓

## Assets (12 of 14; forge-1.21.1 pending — GC thrashing; aprism pending — API path)

- Despotes-v26.3-native-26.2.jar
- Despotes-v26.3-native-1.21.4.jar
- Despotes-v26.3-native-1.21.1.jar
- Despotes-v26.3-native-1.20.1.jar
- Despotes-v26.3-neoforge-26.2.jar
- Despotes-v26.3-neoforge-1.21.4.jar
- Despotes-v26.3-neoforge-1.21.1.jar
- Despotes-v26.3-fabric-26.2.jar
- Despotes-v26.3-fabric-1.21.4.jar
- Despotes-v26.3-fabric-1.21.1.jar
- Despotes-v26.3-fabric-1.20.1.jar
- Despotes-v26.3-forge-1.20.1.jar

---

# Despotes v26.3（正式版）中文说明

v26.3 系列正式稳定版。新增物品栏操作、配方查询、合成、交互、交易、整理等动作。

## 验证

在 fabric-26.2（MC 26.2）上完成全回归测试，全部通过。
