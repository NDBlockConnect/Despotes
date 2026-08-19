# Despotes v26.6-Alpha.1 — Combat System / 战斗系统

**English**

Adds `attack-entity`, `combat`, `retreat`, and `shield` actions.

## New actions

### `attack-entity` — attack by UUID
```json
{"type":"attack-entity","uuid":"12345678-..."}
```
Looks at the entity, then attacks on the next tick.

### `combat` — combat query
```json
{"type":"combat","radius":16}
```
Returns nearby hostile mobs and projectiles (reuses `threats` probe).

### `retreat` — flee from nearest threat
```json
{"type":"retreat","radius":16}
```
Finds the nearest hostile, calculates a direction away from it, and navigates there via `gotoCoords`.

### `shield` — raise/lower shield
```json
{"type":"shield","op":"raise"}
```
Uses `worldUseItem` to trigger shield raise.

## Assets (13 of 14; aprism pending)

---

**中文**

新增 `attack-entity`（按 UUID 攻击）、`combat`（战斗查询）、`retreat`（撤退）、`shield`（盾牌）动作。
