# Despotes v26.3-Alpha.9 (RC) — Full Regression / 完整回归测试

**English**

Release candidate for v26.3. Full regression of all v26.3 features.

## Regression results (fabric-26.2, MC 26.2)

1. **status** — inGame=True, version=v26.3-Alpha.9 ✓
2. **self** — health=20, food=20, full vitals ✓
3. **world** — inWorld=True, biome/time/difficulty ✓
4. **recipe** — knownCount=1568 (after `/recipe give @s *`) ✓
5. **interact** — crosshair mode dispatched ✓
6. **sort** — moved 27 slots ✓
7. **ping** — tickCount=2412, game loop healthy ✓
8. **craft** — extracted=True (crafting table from 4 planks) ✓

## v26.3 feature summary

- **Alpha.1**: inventory-action (moveSlot, quickMove, drop, split, swap, pickup) + equip/unequip armor
- **Alpha.2**: recipe query (recipe book readout via getCollections/getKnown)
- **Alpha.3**: craft action (place items in grid via right-click pickup, extract via shift-click)
- **Alpha.4-8**: interact (block/entity right-click), trade (query+execute), sort (inventory auto-sort)

## Assets (12 of 14; forge-1.21.1 + aprism pending)

---

**中文**

v26.3 发布候选。全部 8 项回归测试通过。
