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
import java.util.Comparator;

/**
 * Data-level world perception (issue 3): block/entity/target/world snapshots read straight
 * from the client's own state, independent of what the framebuffer shows.
 *
 * <p>Version-tolerant: volatile fields (time/seed/biome) are read reflectively so this class
 * compiles against every supported MC version; missing members degrade gracefully.
 */
public final class WorldProbes {

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

    private static Long callLong(Object o, String method) {
        try {
            Method m = o.getClass().getMethod(method);
            return ((Number) m.invoke(o)).longValue();
        } catch (Throwable t) {
            return null;
        }
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
