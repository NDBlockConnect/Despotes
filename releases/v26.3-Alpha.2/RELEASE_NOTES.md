# Despotes v26.3-Alpha.2 — Recipe Query / 配方查询

**English**

Adds a `recipe` query action that returns all unlocked recipes from the player's recipe book.

## New query: `recipe`

```json
{"type":"recipe"}
```

Returns the recipe book contents:

```json
{
  "source": "recipe_book_collections",
  "knownCount": 1568,
  "known": [
    {"id": "minecraft:crafting_table", "type": "ShapedRecipe", "result": "minecraft:crafting_table", "resultCount": 1},
    ...
  ],
  "highlightedCount": 0,
  "highlighted": []
}
```

### Implementation

- **MC 26.x**: `ClientRecipeBook.getCollections()` → `RecipeCollection.getRecipes()` returns `RecipeDisplayEntry` objects
- **MC 1.20.x/1.21.x**: `RecipeBook.getKnown()` returns `Set<ResourceLocation>` resolved via `RecipeManager.byId()`
- Falls back to `RecipeManager.getRecipes()` when recipe book is empty
- Fully reflective and version-tolerant across all supported MC versions

### SecurityGate

Updated `isQuery()` to include all read-only query types (`recipe`, `inventory`, `self`, `threats`, `world`, `blocks`, `entities`, `target`, `container`) so they run inline on the client thread instead of being queued.

## Bug fix

- `native-1.21.4/build.gradle`: updated Loom cache jar paths from old workspace location to current workspace

## Verification (tested before release)

- Fabric 26.2 (MC 26.2) runtime:
  - `/recipe give @s *` → recipe query returns 1568 recipes ✓
  - Query type works inline (no queue wait) ✓

## Assets (13 of 14; forge-1.21.1 pending — GC thrashing in build env)

- Despotes-v26.3-Alpha.2-native-26.2.jar
- Despotes-v26.3-Alpha.2-native-1.21.4.jar
- Despotes-v26.3-Alpha.2-native-1.21.1.jar
- Despotes-v26.3-Alpha.2-native-1.20.1.jar
- Despotes-v26.3-Alpha.2-neoforge-26.2.jar
- Despotes-v26.3-Alpha.2-neoforge-1.21.4.jar
- Despotes-v26.3-Alpha.2-neoforge-1.21.1.jar
- Despotes-v26.3-Alpha.2-fabric-26.2.jar
- Despotes-v26.3-Alpha.2-fabric-1.21.4.jar
- Despotes-v26.3-Alpha.2-fabric-1.21.1.jar
- Despotes-v26.3-Alpha.2-fabric-1.20.1.jar
- Despotes-v26.3-Alpha.2-forge-1.20.1.jar
- (forge-1.21.1 pending — build env GC issue)

---

**中文**

新增 `recipe` 查询动作，返回玩家配方书中所有已解锁的配方。

## 新查询：`recipe`

```json
{"type":"recipe"}
```

### 实现

- MC 26.x：`ClientRecipeBook.getCollections()` → `RecipeCollection.getRecipes()`
- MC 1.20.x/1.21.x：`RecipeBook.getKnown()` 返回 `Set<ResourceLocation>`，通过 `RecipeManager.byId()` 解析
- 全反射跨版本兼容

## 验证

在 fabric-26.2（MC 26.2）上实测：`/recipe give @s *` 后查询返回 1568 条配方 ✓
