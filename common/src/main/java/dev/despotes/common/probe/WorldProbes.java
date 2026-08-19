package dev.despotes.common.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data-level world perception (issue 3): block/entity/target/world snapshots read straight
 * from the client's own state, independent of what the framebuffer shows.
 *
 * <p>Version-tolerant: volatile fields (time/seed/biome) are read reflectively so this class
 * compiles against every supported MC version; missing members degrade gracefully.
 *
 * <p>v26.2-Alpha.2 adds {@link #self(Minecraft)} (extended vitals) and
 * {@link #threats(Minecraft, double)} (hostile awareness). Both are fully reflective so
 * the shared source still compiles on every supported version line.
 */
public final class WorldProbes {

    /** Hostile mob type ids (registry names; stable across versions). */
    private static final Set<String> HOSTILE_IDS = Set.of(
            "minecraft:zombie", "minecraft:zombie_villager", "minecraft:husk", "minecraft:drowned",
            "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton", "minecraft:bogged",
            "minecraft:creeper", "minecraft:spider", "minecraft:cave_spider", "minecraft:enderman",
            "minecraft:silverfish", "minecraft:endermite", "minecraft:witch", "minecraft:slime",
            "minecraft:magma_cube", "minecraft:ghast", "minecraft:blaze", "minecraft:piglin",
            "minecraft:piglin_brute", "minecraft:hoglin", "minecraft:zoglin", "minecraft:ravager",
            "minecraft:vindicator", "minecraft:evoker", "minecraft:illusioner", "minecraft:pillager",
            "minecraft:guardian", "minecraft:elder_guardian", "minecraft:shulker", "minecraft:phantom",
            "minecraft:vex", "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden",
            "minecraft:breeze", "minecraft:creaking");

    /** Hostile projectile type ids. */
    private static final Set<String> PROJECTILE_IDS = Set.of(
            "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:trident", "minecraft:fireball",
            "minecraft:small_fireball", "minecraft:dragon_fireball", "minecraft:wither_skull",
            "minecraft:shulker_bullet", "minecraft:wind_charge", "minecraft:llama_spit");

    private WorldProbes() {
    }

    public static JsonObject world(Minecraft mc) {
        JsonObject o = new JsonObject();
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null) {
            o.addProperty("inWorld", false);
            return o;
        }
        o.addProperty("inWorld", true);
        o.addProperty("dimension", String.valueOf(level.dimension()));
        Long day = callLong(level, "getDayTime");
        if (day != null) {
            o.addProperty("dayTime", day);
            // Derived (no API dependency): 0-12000 ticks is daytime.
            o.addProperty("isDay", day % 24000 < 13000);
        }
        Long game = callLong(level, "getGameTime");
        if (game != null) {
            o.addProperty("gameTime", game);
        }
        o.addProperty("isRaining", level.isRaining());
        o.addProperty("isThundering", level.isThundering());
        o.addProperty("difficulty", String.valueOf(level.getDifficulty()));
        o.addProperty("players", level.players().size());
        Long seed = callLong(level, "getSeed");
        if (seed != null) {
            o.addProperty("seed", seed);
        }
        try {
            Method gm = level.getClass().getMethod("getBiome", BlockPos.class);
            Object biomeHolder = gm.invoke(level, player.blockPosition());
            Object biome = biomeHolder.getClass().getMethod("value").invoke(biomeHolder);
            Field reg = BuiltInRegistries.class.getField("BIOME");
            Object registry = reg.get(null);
            Method getKey = registry.getClass().getMethod("getKey", Object.class);
            o.addProperty("biome", String.valueOf(getKey.invoke(registry, biome)));
        } catch (Throwable ignored) {
        }
        return o;
    }

    public static JsonObject blocks(Minecraft mc, int x, int y, int z, int r) {
        JsonObject o = new JsonObject();
        var level = mc.level;
        if (level == null) {
            return o;
        }
        JsonArray cells = new JsonArray();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = new BlockPos(x + dx, y + dy, z + dz);
                    BlockState st = level.getBlockState(p);
                    if (st.isAir()) {
                        continue;
                    }
                    JsonObject c = new JsonObject();
                    c.addProperty("x", p.getX());
                    c.addProperty("y", p.getY());
                    c.addProperty("z", p.getZ());
                    c.addProperty("block", String.valueOf(
                            BuiltInRegistries.BLOCK.getKey(st.getBlock())));
                    cells.add(c);
                }
            }
        }
        o.addProperty("cx", x);
        o.addProperty("cy", y);
        o.addProperty("cz", z);
        o.addProperty("radius", r);
        o.add("blocks", cells);
        return o;
    }

    public static JsonObject entities(Minecraft mc, double radius) {
        JsonObject o = new JsonObject();
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null) {
            o.add("entities", new JsonArray());
            return o;
        }
        AABB box = new AABB(player.blockPosition()).inflate(radius);
        JsonArray arr = new JsonArray();
        level.getEntities(player, box, e -> e != player)
                .stream()
                .sorted(Comparator.comparingDouble(player::distanceTo))
                .limit(40)
                .forEach(e -> arr.add(entityJson(e, player)));
        o.add("entities", arr);
        return o;
    }

    /**
     * v26.2-Alpha.2 threat awareness: hostile mobs and incoming projectiles within
     * {@code radius}, nearest first, capped at 20. Each entry carries distance, health and
     * whether the mob currently targets the local player.
     */
    public static JsonObject threats(Minecraft mc, double radius) {
        JsonObject o = new JsonObject();
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null) {
            o.add("threats", new JsonArray());
            return o;
        }
        AABB box = new AABB(player.blockPosition()).inflate(radius);
        JsonArray arr = new JsonArray();
        level.getEntities(player, box, e -> e != player && isThreat(e))
                .stream()
                .sorted(Comparator.comparingDouble(player::distanceTo))
                .limit(20)
                .forEach(e -> {
                    JsonObject j = entityJson(e, player);
                    String id = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
                    j.addProperty("kind", PROJECTILE_IDS.contains(id) ? "projectile" : "monster");
                    j.addProperty("health", callFloat(e, "getHealth"));
                    j.addProperty("targetingYou", targetsPlayer(e, player));
                    arr.add(j);
                });
        o.addProperty("radius", radius);
        o.addProperty("count", arr.size());
        o.add("threats", arr);
        return o;
    }

    private static boolean isThreat(Entity e) {
        String id = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
        return HOSTILE_IDS.contains(id) || PROJECTILE_IDS.contains(id);
    }

    /** True when this entity is a Mob whose current attack target is the local player. */
    private static boolean targetsPlayer(Entity e, Entity player) {
        try {
            Object target = e.getClass().getMethod("getTarget").invoke(e);
            return target == player;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * v26.2-Alpha.2 extended self perception: vitals beyond health (food, armor, air,
     * effects), state flags (fire/water/sprint/sneak/elytra), motion, XP and the immediate
     * block environment. Everything past the core fields is reflective and degrades
     * gracefully when a member is missing on a given MC version.
     */
    public static JsonObject self(Minecraft mc) {
        JsonObject o = new JsonObject();
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null) {
            o.addProperty("inWorld", false);
            return o;
        }
        o.addProperty("inWorld", true);

        // Vitals
        o.addProperty("health", player.getHealth());
        o.addProperty("maxHealth", callFloat(player, "getMaxHealth"));
        o.addProperty("armor", callInt(player, "getArmorValue"));
        o.addProperty("absorption", callFloat(player, "getAbsorptionAmount"));
        Object food = call(player, "getFoodData");
        if (food != null) {
            o.addProperty("food", callInt(food, "getFoodLevel"));
            o.addProperty("saturation", callFloat(food, "getSaturationLevel"));
        }
        o.addProperty("airSupply", callInt(player, "getAirSupply"));
        o.addProperty("maxAirSupply", callInt(player, "getMaxAirSupply"));

        // Experience (public fields on Player across versions)
        Integer xpLevel = fieldInt(player, "experienceLevel");
        if (xpLevel != null) {
            o.addProperty("xpLevel", xpLevel);
        }
        Float xpProgress = fieldFloat(player, "experienceProgress");
        if (xpProgress != null) {
            o.addProperty("xpProgress", xpProgress);
        }

        // State flags
        o.addProperty("onFire", callBool(player, "isOnFire"));
        o.addProperty("fireTicks", callInt(player, "getRemainingFireTicks"));
        o.addProperty("inWater", callBool(player, "isInWater"));
        o.addProperty("inLava", callBool(player, "isInLava"));
        o.addProperty("underwater", callBool(player, "isUnderWater"));
        o.addProperty("sprinting", callBool(player, "isSprinting"));
        o.addProperty("sneaking", callBool(player, "isShiftKeyDown"));
        o.addProperty("swimming", callBool(player, "isSwimming"));
        o.addProperty("fallFlying", callBool(player, "isFallFlying"));
        o.addProperty("onGround", onGround(player));
        o.addProperty("fallDistance", fieldFloat(player, "fallDistance"));
        o.addProperty("inVehicle", call(player, "getVehicle") != null);

        // Motion
        try {
            Object delta = player.getClass().getMethod("getDeltaMovement").invoke(player);
            double vx = ((Number) delta.getClass().getMethod("x").invoke(delta)).doubleValue();
            double vy = ((Number) delta.getClass().getMethod("y").invoke(delta)).doubleValue();
            double vz = ((Number) delta.getClass().getMethod("z").invoke(delta)).doubleValue();
            JsonObject v = new JsonObject();
            v.addProperty("x", vx);
            v.addProperty("y", vy);
            v.addProperty("z", vz);
            o.add("velocity", v);
        } catch (Throwable ignored) {
        }

        // Active status effects
        JsonArray eff = new JsonArray();
        try {
            Object collection = player.getClass().getMethod("getActiveEffects").invoke(player);
            if (collection instanceof Collection<?> list) {
                Field reg = BuiltInRegistries.class.getField("MOB_EFFECT");
                Object registry = reg.get(null);
                Method getKey = registry.getClass().getMethod("getKey", Object.class);
                for (Object inst : list) {
                    JsonObject j = new JsonObject();
                    Object holder = inst.getClass().getMethod("getEffect").invoke(inst);
                    Object effect;
                    try {
                        effect = holder.getClass().getMethod("value").invoke(holder);
                    } catch (Throwable t) {
                        effect = holder; // pre-holder versions return the effect directly
                    }
                    j.addProperty("effect", String.valueOf(getKey.invoke(registry, effect)));
                    j.addProperty("amplifier", callInt(inst, "getAmplifier"));
                    j.addProperty("durationTicks", callInt(inst, "getDuration"));
                    eff.add(j);
                }
            }
        } catch (Throwable ignored) {
        }
        o.add("effects", eff);

        // Immediate environment
        o.addProperty("light", callIntArg(level, "getMaxLocalRawBrightness",
                BlockPos.class, player.blockPosition()));
        o.addProperty("blockAtFeet", blockIdAt(level, player.blockPosition()));
        o.addProperty("blockBelow", blockIdAt(level, player.blockPosition().below()));
        o.addProperty("blockAtHead", blockIdAt(level, player.blockPosition().above()));
        return o;
    }

    public static JsonObject target(Minecraft mc) {
        JsonObject o = new JsonObject();
        var player = mc.player;
        HitResult hit = mc.hitResult;
        if (player == null || hit == null) {
            o.addProperty("type", "none");
            return o;
        }
        if (hit instanceof EntityHitResult ehr) {
            o.addProperty("type", "entity");
            o.add("entity", entityJson(ehr.getEntity(), player));
        } else if (hit instanceof BlockHitResult bhr) {
            o.addProperty("type", "block");
            o.addProperty("x", bhr.getBlockPos().getX());
            o.addProperty("y", bhr.getBlockPos().getY());
            o.addProperty("z", bhr.getBlockPos().getZ());
            o.addProperty("face", String.valueOf(bhr.getDirection()));
            if (mc.level != null) {
                BlockState st = mc.level.getBlockState(bhr.getBlockPos());
                o.addProperty("block", String.valueOf(
                        BuiltInRegistries.BLOCK.getKey(st.getBlock())));
            }
        } else {
            o.addProperty("type", "miss");
        }
        o.addProperty("distance", hit.getLocation() == null
                ? -1 : hit.getLocation().distanceTo(player.position()));
        return o;
    }

    /** v26.4-Alpha.1: nearby players query (name/UUID/distance/health/gear). */
    public static JsonObject players(net.minecraft.client.Minecraft mc, double radius) {
        JsonObject o = new JsonObject();
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null) {
            o.add("players", new JsonArray());
            return o;
        }
        AABB box = new AABB(player.blockPosition()).inflate(radius);
        JsonArray arr = new JsonArray();
        level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, box,
                        e -> e != player && e.distanceTo(player) <= radius)
                .stream()
                .sorted(Comparator.comparingDouble(player::distanceTo))
                .limit(20)
                .forEach(e -> {
                    JsonObject j = new JsonObject();
                    j.addProperty("name", e.getName().getString());
                    j.addProperty("uuid", e.getStringUUID());
                    j.addProperty("x", e.getX());
                    j.addProperty("y", e.getY());
                    j.addProperty("z", e.getZ());
                    j.addProperty("distance", Math.round(e.distanceTo(player) * 100) / 100.0);
                    j.addProperty("health", e.getHealth());
                    j.addProperty("armor", callInt(e, "getArmorValue"));
                    j.addProperty("sprinting", callBool(e, "isSprinting"));
                    j.addProperty("sneaking", callBool(e, "isShiftKeyDown"));
                    arr.add(j);
                });
        o.addProperty("radius", radius);
        o.addProperty("count", arr.size());
        o.add("players", arr);
        return o;
    }

    /** v26.4-Alpha.2: server info query (MOTD, online count, ping). */
    public static JsonObject server(net.minecraft.client.Minecraft mc) {
        JsonObject o = new JsonObject();
        try {
            Object conn = mc.getClass().getMethod("getConnection").invoke(mc);
            if (conn == null) {
                o.addProperty("connected", false);
                return o;
            }
            o.addProperty("connected", true);
            // Server data
            Object serverData = call(mc, "getCurrentServer");
            if (serverData != null) {
                o.addProperty("ip", callStr(serverData, "ip"));
                o.addProperty("name", callStr(serverData, "name"));
                o.addProperty("motd", callStr(serverData, "motd"));
                o.addProperty("ping", callLong(serverData, "ping"));
            }
            // Online player count from connection
            o.addProperty("onlinePlayers", callInt(conn, "getOnlinePlayers"));
        } catch (Throwable t) {
            o.addProperty("error", "server query failed: " + t.getMessage());
        }
        return o;
    }

    /** v26.4-Alpha.3: tablist query (player names with latency). */
    public static JsonObject tablist(net.minecraft.client.Minecraft mc) {
        JsonObject o = new JsonObject();
        try {
            Object playerList = null;
            // getConnection().getList() or getOnlinePlayers()
            Object conn = mc.getClass().getMethod("getConnection").invoke(mc);
            if (conn != null) {
                // ClientPacketListener.getPlayerInfoMap() / getOnlinePlayers()
                for (String mn : new String[]{"getPlayerInfoMap", "getOnlinePlayers", "getList"}) {
                    try {
                        Object result = conn.getClass().getMethod(mn).invoke(conn);
                        if (result instanceof Collection<?> c) {
                            JsonArray arr = new JsonArray();
                            for (Object info : c) {
                                JsonObject j = new JsonObject();
                                // PlayerInfo.getName() / getProfile().getName()
                                try {
                                    Object profile = info.getClass().getMethod("getProfile").invoke(info);
                                    String name = (String) profile.getClass().getMethod("getName").invoke(profile);
                                    j.addProperty("name", name != null ? name : "");
                                } catch (Throwable ignored) {}
                                try {
                                    int latency = ((Number) info.getClass().getMethod("getLatency").invoke(info)).intValue();
                                    j.addProperty("latency", latency);
                                } catch (Throwable ignored) {}
                                try {
                                    int gamemode = ((Number) info.getClass().getMethod("getGameMode").invoke(info)).intValue();
                                    j.addProperty("gamemode", gamemode);
                                } catch (Throwable ignored) {}
                                arr.add(j);
                            }
                            o.add("players", arr);
                            o.addProperty("count", arr.size());
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            o.addProperty("error", "tablist query failed: " + t.getMessage());
        }
        return o;
    }

    /** v26.4-Alpha.4: scoreboard query (teams, scores, objectives). */
    public static JsonObject scoreboard(net.minecraft.client.Minecraft mc) {
        JsonObject o = new JsonObject();
        try {
            if (mc.level == null) {
                o.addProperty("inWorld", false);
                return o;
            }
            Object scoreboard = mc.level.getClass().getMethod("getScoreboard").invoke(mc.level);
            if (scoreboard == null) return o;
            // Teams
            JsonArray teams = new JsonArray();
            try {
                Object teamCollection = scoreboard.getClass().getMethod("getPlayerTeams").invoke(scoreboard);
                if (teamCollection instanceof Collection<?> c) {
                    for (Object team : c) {
                        JsonObject t = new JsonObject();
                        try { t.addProperty("name", (String) team.getClass().getMethod("getName").invoke(team)); } catch (Throwable ignored) {}
                        try { t.addProperty("displayName", ((net.minecraft.network.chat.Component) team.getClass().getMethod("getDisplayName").invoke(team)).getString()); } catch (Throwable ignored) {}
                        try { t.addProperty("color", String.valueOf(team.getClass().getMethod("getColor").invoke(team))); } catch (Throwable ignored) {}
                        teams.add(t);
                    }
                }
            } catch (Throwable ignored) {}
            o.add("teams", teams);
            // Objectives
            JsonArray objectives = new JsonArray();
            try {
                Object objCollection = scoreboard.getClass().getMethod("getObjectives").invoke(scoreboard);
                if (objCollection instanceof Collection<?> c) {
                    for (Object obj : c) {
                        JsonObject ob = new JsonObject();
                        try { ob.addProperty("name", (String) obj.getClass().getMethod("getName").invoke(obj)); } catch (Throwable ignored) {}
                        try { ob.addProperty("displayName", ((net.minecraft.network.chat.Component) obj.getClass().getMethod("getDisplayName").invoke(obj)).getString()); } catch (Throwable ignored) {}
                        try { ob.addProperty("criteria", String.valueOf(obj.getClass().getMethod("getCriteria").invoke(obj))); } catch (Throwable ignored) {}
                        objectives.add(ob);
                    }
                }
            } catch (Throwable ignored) {}
            o.add("objectives", objectives);
        } catch (Throwable t) {
            o.addProperty("error", "scoreboard query failed: " + t.getMessage());
        }
        return o;
    }

    /** v26.4-Alpha.7: coords query (spawn, world border, key locations). */
    public static JsonObject coords(net.minecraft.client.Minecraft mc) {
        JsonObject o = new JsonObject();
        if (mc.level == null || mc.player == null) {
            o.addProperty("inWorld", false);
            return o;
        }
        // Spawn point
        try {
            Object spawnPos = mc.level.getClass().getMethod("getSharedSpawnPos").invoke(mc.level);
            if (spawnPos != null) {
                JsonObject s = new JsonObject();
                s.addProperty("x", ((Number) spawnPos.getClass().getMethod("getX").invoke(spawnPos)).intValue());
                s.addProperty("y", ((Number) spawnPos.getClass().getMethod("getY").invoke(spawnPos)).intValue());
                s.addProperty("z", ((Number) spawnPos.getClass().getMethod("getZ").invoke(spawnPos)).intValue());
                o.add("spawn", s);
            }
        } catch (Throwable ignored) {}
        // World border
        try {
            Object border = mc.level.getClass().getMethod("getWorldBorder").invoke(mc.level);
            if (border != null) {
                JsonObject b = new JsonObject();
                b.addProperty("centerX", ((Number) border.getClass().getMethod("getCenterX").invoke(border)).doubleValue());
                b.addProperty("centerZ", ((Number) border.getClass().getMethod("getCenterZ").invoke(border)).doubleValue());
                b.addProperty("size", ((Number) border.getClass().getMethod("getSize").invoke(border)).doubleValue());
                b.addProperty("damageSafeZone", ((Number) border.getClass().getMethod("getDamageSafeZone").invoke(border)).doubleValue());
                b.addProperty("damagePerBlock", ((Number) border.getClass().getMethod("getDamagePerBlock").invoke(border)).doubleValue());
                o.add("worldBorder", b);
            }
        } catch (Throwable ignored) {}
        // Player position
        JsonObject pos = new JsonObject();
        pos.addProperty("x", mc.player.getX());
        pos.addProperty("y", mc.player.getY());
        pos.addProperty("z", mc.player.getZ());
        o.add("player", pos);
        return o;
    }

    private static String callStr(Object o, String method) {
        try {
            Object v = resolve(o, method).invoke(o);
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** v26.3-Alpha.2: recipe book readout (known + highlighted recipes). */
    @SuppressWarnings("unchecked")
    public static JsonObject recipes(net.minecraft.client.Minecraft mc) {
        JsonObject o = new JsonObject();
        if (mc.player == null) {
            o.addProperty("inWorld", false);
            return o;
        }
        try {
            // Resolve RecipeManager: try multiple access paths across versions
            Object recipeMgr = null;
            // Path 1: mc.level.getRecipeManager()
            if (mc.level != null) {
                recipeMgr = call(mc.level, "getRecipeManager");
                if (recipeMgr == null) recipeMgr = call(mc.level, "getRecipeAccess");
            }
            // Path 2: mc.player.connection.getRecipeManager()
            if (recipeMgr == null && mc.player != null) {
                Object conn = call(mc.player, "connection");
                if (conn == null) conn = call(mc, "getConnection");
                if (conn != null) {
                    recipeMgr = call(conn, "getRecipeManager");
                    if (recipeMgr == null) {
                        try { recipeMgr = findField(conn.getClass(), "recipeManager").get(conn); } catch (Throwable ignored) {}
                    }
                }
            }

            // RecipeBook: mc.player.getRecipeBook()
            Object recipeBook = call(mc.player, "getRecipeBook");

            // Known recipe IDs — try multiple method names across versions
            // MC 26.x: getCollections() returns List<RecipeCollection>
            // MC 1.20.x/1.21.x: getKnown() returns Set<ResourceLocation>
            java.util.Collection<Object> knownIds = null;
            java.util.Collection<Object> recipeCollections = null;
            if (recipeBook != null) {
                // Try getKnown() first (1.20.x/1.21.x)
                for (String methodName : new String[]{"getKnown", "known"}) {
                    try {
                        Object knownSet = recipeBook.getClass().getMethod(methodName).invoke(recipeBook);
                        if (knownSet instanceof Collection<?> c) {
                            knownIds = (Collection<Object>) c;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
                // Try getCollections() (26.x)
                if (knownIds == null || knownIds.isEmpty()) {
                    try {
                        Object colls = recipeBook.getClass().getMethod("getCollections").invoke(recipeBook);
                        if (colls instanceof Collection<?> c) {
                            recipeCollections = (Collection<Object>) c;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // Highlighted recipe IDs
            java.util.Collection<Object> highlightedIds = null;
            if (recipeBook != null) {
                for (String methodName : new String[]{"getHighlighted", "highlighted"}) {
                    try {
                        Object hlSet = recipeBook.getClass().getMethod(methodName).invoke(recipeBook);
                        if (hlSet instanceof Collection<?> c) {
                            highlightedIds = (Collection<Object>) c;
                            break;
                        }
                    } catch (Throwable ignored) {}
                    }
                }

            // If we have RecipeCollections (MC 26.x), extract recipes from them
            if (recipeCollections != null && !recipeCollections.isEmpty()) {
                JsonArray knownArr = new JsonArray();
                for (Object coll : recipeCollections) {
                    try {
                        // RecipeCollection.getRecipes() returns List<RecipeHolder>
                        Object recipes = coll.getClass().getMethod("getRecipes").invoke(coll);
                        if (recipes instanceof Collection<?> rc) {
                            for (Object holder : rc) {
                                JsonObject r = recipeEntry(holder);
                                if (r != null) knownArr.add(r);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                o.addProperty("source", "recipe_book_collections");
                o.addProperty("knownCount", knownArr.size());
                o.add("known", knownArr);
                o.addProperty("highlightedCount", 0);
                o.add("highlighted", new JsonArray());
                return o;
            }

            // If recipe book is empty/unavailable, get all recipes from RecipeManager
            if ((knownIds == null || knownIds.isEmpty()) && recipeMgr != null) {
                Object allRecipes = null;
                String usedMethod = null;
                for (String mn : new String[]{"getRecipes", "getAllRecipes", "values", "recipeCollection"}) {
                    try {
                        allRecipes = recipeMgr.getClass().getMethod(mn).invoke(recipeMgr);
                        if (allRecipes != null) { usedMethod = mn; break; }
                    } catch (Throwable ignored) {}
                }
                if (allRecipes instanceof Collection<?> c) {
                    JsonArray knownArr = new JsonArray();
                    for (Object recipeHolder : c) {
                        JsonObject r = recipeEntry(recipeHolder);
                        if (r != null) knownArr.add(r);
                    }
                    o.addProperty("source", "recipe_manager:" + usedMethod);
                    o.addProperty("knownCount", knownArr.size());
                    o.add("known", knownArr);
                    o.addProperty("highlightedCount", 0);
                    o.add("highlighted", new JsonArray());
                    return o;
                } else if (allRecipes instanceof java.util.Map<?,?> m) {
                    // Some versions return a Map<ResourceLocation, RecipeHolder>
                    JsonArray knownArr = new JsonArray();
                    for (Object entry : m.entrySet()) {
                        Object val = ((java.util.Map.Entry<?,?>) entry).getValue();
                        JsonObject r = recipeEntry(val);
                        if (r != null) knownArr.add(r);
                    }
                    o.addProperty("source", "recipe_manager_map:" + usedMethod);
                    o.addProperty("knownCount", knownArr.size());
                    o.add("known", knownArr);
                    o.addProperty("highlightedCount", 0);
                    o.add("highlighted", new JsonArray());
                    return o;
                } else if (allRecipes != null) {
                    o.addProperty("source", "recipe_mgr:" + allRecipes.getClass().getSimpleName());
                }
            } else if (knownIds != null && !knownIds.isEmpty()) {
                o.addProperty("source", "recipe_book");
            }

            // Resolve known recipe IDs to recipe entries via RecipeManager.byId()
            JsonArray knownArr = new JsonArray();
            if (knownIds != null) {
                for (Object idObj : knownIds) {
                    JsonObject r = recipeEntryById(recipeMgr, idObj);
                    if (r != null) knownArr.add(r);
                }
            }
            JsonArray hlArr = new JsonArray();
            if (highlightedIds != null) {
                for (Object idObj : highlightedIds) {
                    JsonObject r = recipeEntryById(recipeMgr, idObj);
                    if (r != null) hlArr.add(r);
                }
            }
            o.addProperty("knownCount", knownArr.size());
            o.add("known", knownArr);
            o.addProperty("highlightedCount", hlArr.size());
            o.add("highlighted", hlArr);
        } catch (Throwable t) {
            o.addProperty("error", "recipe query failed: " + t.getMessage());
        }
        return o;
    }

    /**
     * Resolve a recipe ID (ResourceLocation) to a recipe entry via RecipeManager.byId().
     * Falls back to just the ID string if the manager or recipe is unavailable.
     */
    private static JsonObject recipeEntryById(Object recipeMgr, Object idObj) {
        if (idObj == null) return null;
        JsonObject r = new JsonObject();
        r.addProperty("id", String.valueOf(idObj));
        if (recipeMgr != null) {
            try {
                // RecipeManager.byId(ResourceLocation) returns Optional<RecipeHolder>
                java.util.Optional<?> opt = (java.util.Optional<?>)
                        recipeMgr.getClass().getMethod("byId", idObj.getClass())
                                .invoke(recipeMgr, idObj);
                if (opt != null && opt.isPresent()) {
                    Object holder = opt.get();
                    fillRecipeEntry(r, holder);
                }
            } catch (Throwable ignored) {}
        }
        return r;
    }

    /** Fill in recipe details (result item, type) from a RecipeHolder or Recipe. */
    private static void fillRecipeEntry(JsonObject r, Object recipeHolder) {
        try {
            // RecipeHolder.id() + RecipeHolder.value()
            Object recipe = null;
            try {
                recipe = recipeHolder.getClass().getMethod("value").invoke(recipeHolder);
            } catch (NoSuchMethodException e) {
                recipe = recipeHolder; // pre-RecipeHolder
            }
            if (recipe != null) {
                r.addProperty("type", String.valueOf(recipe.getClass().getSimpleName()));
                try {
                    Object result = recipe.getClass().getMethod("getResult").invoke(recipe);
                    if (result != null) {
                        Object item = result.getClass().getMethod("getItem").invoke(result);
                        Object key = BuiltInRegistries.ITEM.getKey((net.minecraft.world.item.Item) item);
                        r.addProperty("result", String.valueOf(key));
                        r.addProperty("resultCount", ((Number) result.getClass().getMethod("getCount").invoke(result)).intValue());
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Build a JSON entry for a RecipeHolder: extract the recipe id and result item.
     * RecipeHolder (1.21+/26.x) wraps a ResourceLocation id + Recipe; older versions
     * may expose the recipe directly. MC 26.2 uses RecipeDisplayEntry with an id field.
     */
    private static JsonObject recipeEntry(Object recipeHolder) {
        if (recipeHolder == null) return null;
        JsonObject r = new JsonObject();
        try {
            // Try RecipeHolder.id() (1.21.x)
            Object id = null;
            try { id = recipeHolder.getClass().getMethod("id").invoke(recipeHolder); } catch (Throwable ignored) {}
            // Try RecipeDisplayEntry.id() or getId() (26.x)
            if (id == null) {
                for (String mn : new String[]{"id", "getId", "identifier", "name"}) {
                    try { id = recipeHolder.getClass().getMethod(mn).invoke(recipeHolder); if (id != null) break; } catch (Throwable ignored) {}
                }
            }
            r.addProperty("id", String.valueOf(id));

            // Try to get the recipe and its result
            Object recipe = null;
            try {
                recipe = recipeHolder.getClass().getMethod("value").invoke(recipeHolder);
            } catch (NoSuchMethodException e) {
                recipe = recipeHolder; // pre-RecipeHolder or the object IS the recipe
            }
            if (recipe != null) {
                r.addProperty("type", String.valueOf(recipe.getClass().getSimpleName()));
                try {
                    Object result = recipe.getClass().getMethod("getResult").invoke(recipe);
                    if (result != null) {
                        Object item = result.getClass().getMethod("getItem").invoke(result);
                        Object key = BuiltInRegistries.ITEM.getKey((net.minecraft.world.item.Item) item);
                        r.addProperty("result", String.valueOf(key));
                        r.addProperty("resultCount", ((Number) result.getClass().getMethod("getCount").invoke(result)).intValue());
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            return null;
        }
        return r;
    }

    /** Open container (screen menu) snapshot: slots with item + count. */
    public static com.google.gson.JsonObject container(net.minecraft.client.Minecraft mc) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        var menu = mc.player == null ? null : mc.player.containerMenu;
        if (menu == null) {
            o.addProperty("open", false);
            return o;
        }
        o.addProperty("open", true);
        try {
            Object title = menu.getClass().getMethod("getTitle").invoke(menu);
            o.addProperty("title", String.valueOf(
                    title.getClass().getMethod("getString").invoke(title)));
        } catch (Throwable t) {
        }
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        int i = 0;
        for (var slot : menu.slots) {
            var stack = slot.getItem();
            if (stack == null || stack.isEmpty()) {
                i++;
                continue;
            }
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            j.addProperty("slot", i);
            j.addProperty("item", String.valueOf(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())));
            j.addProperty("count", stack.getCount());
            arr.add(j);
            i++;
        }
        o.add("slots", arr);
        return o;
    }

    private static String blockIdAt(Object level, BlockPos pos) {
        try {
            BlockState st = ((net.minecraft.world.level.Level) level).getBlockState(pos);
            return String.valueOf(BuiltInRegistries.BLOCK.getKey(st.getBlock()));
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean onGround(Object e) {
        Boolean f = fieldBool(e, "onGround");
        if (f != null) {
            return f;
        }
        try {
            return (Boolean) e.getClass().getMethod("onGround").invoke(e);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * v26.2-Alpha.6: reflection method cache. {@code getMethod} walks the whole class
     * hierarchy on every call; the {@code self} probe makes ~20 such lookups per request,
     * so resolved Method handles are memoized by (class, method-name). Methods are stable
     * per version, and the cache is bounded by the small number of MC classes involved.
     */
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private static Method resolve(Object o, String method) throws NoSuchMethodException {
        String key = o.getClass().getName() + '#' + method;
        Method m = METHOD_CACHE.get(key);
        if (m == null) {
            m = o.getClass().getMethod(method);
            METHOD_CACHE.put(key, m);
        }
        return m;
    }

    private static Long callLong(Object o, String method) {
        try {
            Method m = resolve(o, method);
            return ((Number) m.invoke(o)).longValue();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object call(Object o, String method) {
        try {
            return resolve(o, method).invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int callInt(Object o, String method) {
        try {
            return ((Number) resolve(o, method).invoke(o)).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int callIntArg(Object o, String method, Class<?> argType, Object arg) {
        try {
            return ((Number) o.getClass().getMethod(method, argType).invoke(o, arg)).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static float callFloat(Object o, String method) {
        try {
            return ((Number) resolve(o, method).invoke(o)).floatValue();
        } catch (Throwable t) {
            return -1f;
        }
    }

    private static boolean callBool(Object o, String method) {
        try {
            Object v = resolve(o, method).invoke(o);
            return v instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Integer fieldInt(Object o, String name) {
        try {
            Field f = findField(o.getClass(), name);
            return ((Number) f.get(o)).intValue();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Float fieldFloat(Object o, String name) {
        try {
            Field f = findField(o.getClass(), name);
            return ((Number) f.get(o)).floatValue();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Boolean fieldBool(Object o, String name) {
        try {
            Field f = findField(o.getClass(), name);
            return (Boolean) f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static JsonObject entityJson(Entity e, Entity player) {
        JsonObject j = new JsonObject();
        j.addProperty("id", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())));
        j.addProperty("name", e.getName().getString());
        j.addProperty("uuid", e.getStringUUID());
        j.addProperty("x", e.getX());
        j.addProperty("y", e.getY());
        j.addProperty("z", e.getZ());
        j.addProperty("distance", Math.round(e.distanceTo(player) * 100) / 100.0);
        return j;
    }
}
