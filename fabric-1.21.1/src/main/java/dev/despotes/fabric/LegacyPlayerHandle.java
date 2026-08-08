package dev.despotes.fabric;

import dev.despotes.common.platform.PlayerHandle;
import net.minecraft.client.player.LocalPlayer;

/** Legacy player handle (official mappings, 1.20 – 1.21.11). */
public final class LegacyPlayerHandle implements PlayerHandle {

    private final LocalPlayer player;

    public LegacyPlayerHandle(LocalPlayer player) {
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
}
