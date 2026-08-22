package dev.despotes.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.despotes.common.platform.PlayerHandle;
import net.minecraft.client.player.LocalPlayer;

/** Legacy NeoForge player handle backed by the live LocalPlayer. */
public final class LegacyNeoForgePlayerHandle implements PlayerHandle {

    private final LocalPlayer player;

    public LegacyNeoForgePlayerHandle(LocalPlayer player) {
        this.player = player;
    }

    @Override
    public String name() {
        return player.getName().getString();
    }

    @Override
    public double x() {
        return player.getX();
    }

    @Override
    public double y() {
        return player.getY();
    }

    @Override
    public double z() {
        return player.getZ();
    }

    @Override
    public float yaw() {
        return player.getYRot();
    }

    @Override
    public float pitch() {
        return player.getXRot();
    }

    @Override
    public float health() {
        return player.getHealth();
    }

    @Override
    public String dimension() {
        return String.valueOf(player.level().dimension().location());
    }

    @Override
    public int selectedHotbarSlot() {
        return player.getInventory().selected;
    }

    @Override
    public JsonObject inventoryJson() {
        JsonObject o = new JsonObject();
        var inv = player.getInventory();
        o.addProperty("selectedSlot", inv.selected);
        JsonArray hotbar = new JsonArray();
        JsonArray slots = new JsonArray();
        for (int i = 0; i < inv.items.size(); i++) {
            var stack = inv.items.get(i);
            String id = (stack == null || stack.isEmpty())
                    ? "" : String.valueOf(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (i < 9) {
                hotbar.add(id);
            }
            if (stack != null && !stack.isEmpty()) {
                JsonObject s = new JsonObject();
                s.addProperty("slot", i);
                s.addProperty("item", id);
                s.addProperty("count", stack.getCount());
                slots.add(s);
            }
        }
        o.add("hotbar", hotbar);
        o.add("slots", slots);
        return o;
    }
}
