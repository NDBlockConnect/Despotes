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
import java.util.Set;

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

    private static Long callLong(Object o, String method) {
        try {
            Method m = o.getClass().getMethod(method);
            return ((Number) m.invoke(o)).longValue();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object call(Object o, String method) {
        try {
            return o.getClass().getMethod(method).invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int callInt(Object o, String method) {
        try {
            return ((Number) o.getClass().getMethod(method).invoke(o)).intValue();
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
            return ((Number) o.getClass().getMethod(method).invoke(o)).floatValue();
        } catch (Throwable t) {
            return -1f;
        }
    }

    private static boolean callBool(Object o, String method) {
        try {
            Object v = o.getClass().getMethod(method).invoke(o);
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
