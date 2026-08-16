# Despotes v26.3-Alpha.1 — Inventory Action & Equipment / 物品栏操作与装备

**English**

The first release of the v26.3 line introduces programmatic inventory manipulation and armor equipment management.

## New actions

### `inventory-action` — slot manipulation in open container menus

```json
{"type":"inventory-action","op":"quickMove","slot":41}
```

Six operations:
- **`moveSlot`**: pick up from `fromSlot`, place at `toSlot` (pickup + pickup)
- **`quickMove`**: shift-click a slot (auto-routes armor to the correct armor slot)
- **`drop`**: click outside to drop — `stack:true` drops entire stack, `stack:false` drops one
- **`split`**: right-click to split a stack in half
- **`swap`**: swap a slot with a hotbar slot (SWAP click type, button = hotbar 0-8)
- **`pickup`**: basic left/right click on a slot

### `equip` — armor equip/unequip

```json
{"type":"equip","op":"equip","piece":"chestplate","slot":41}
{"type":"equip","op":"unequip","piece":"helmet"}
```

Supports: `helmet` (slot 5), `chestplate` (slot 6), `leggings` (slot 7), `boots` (slot 8).
Uses pickup+place at the InventoryMenu armor slots.

## Implementation

- `IGamePlatform.slotClick()`: cross-version reflective container slot click
  - 26.x: `MultiPlayerGameMode.handleContainerInput(containerId, slot, button, ContainerInput, Player)`
  - 1.20.x/1.21.x: `handleInventoryMouseClick(containerId, slot, button, ClickType, Player)`
  - Fallback: `AbstractContainerMenu.clicked()` directly
- `Player` superclass resolved for method lookup (methods declare `Player`, not `LocalPlayer`)

## Verification (tested before release)

- Fabric 26.2 (MC 26.2) runtime:
  - `quickMove` on iron_helmet slot 41 → moved to armor slot 5 (armor 0→2)
  - `drop` on iron_chestplate → removed from inventory
  - `split` on dirt stack 64 → halved to 32
  - `swap` with hotbar slot 1 → items exchanged
  - `equip chestplate` from slot 41 → armor slot 6 (armor 2→8)
  - `pickup` basic click → works
  - `moveSlot` pickup+place → works

## Assets (8 of 14; neoforge/forge/aprism pending)

- Despotes-v26.3-Alpha.1-fabric-26.2.jar
- Despotes-fabric-1.21.4-v26.3-Alpha.1-fabric-1.21.4.jar
- Despotes-fabric-1.21.1-v26.3-Alpha.1-fabric-1.21.1.jar
- Despotes-fabric-1.20.1-v26.3-Alpha.1-fabric-1.20.1.jar
- Despotes-v26.3-Alpha.1-native-26.2.jar
- Despotes-v26.3-Alpha.1-native-1.21.4.jar
- Despotes-v26.3-Alpha.1-native-1.21.1.jar
- Despotes-v26.3-Alpha.1-native-1.20.1.jar

---

**中文**

v26.3 系列首版引入编程式物品栏操作和装备管理。

## 新动作

### `inventory-action` — 容器菜单槽位操作

六种操作：`moveSlot`（移动槽位）、`quickMove`（shift点击自动路由）、`drop`（丢弃）、`split`（右键分半）、`swap`（与热栏交换）、`pickup`（基础点击）。

### `equip` — 装备穿脱

支持 helmet/chestplate/leggings/boots，通过 pickup+place 操作 InventoryMenu 护甲槽（5-8）。

## 验证

在 fabric-26.2（MC 26.2）上实测：quickMove 装备头盔（护甲 0→2）、drop 丢弃物品、split 泥土 64→32、swap 交换热栏、equip 胸甲（护甲 2→8）全部通过。
