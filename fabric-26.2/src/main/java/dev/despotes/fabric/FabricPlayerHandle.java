package dev.despotes.fabric;

import dev.despotes.common.platform.PlayerHandle;
import net.minecraft.client.player.LocalPlayer;

/** Fabric player handle backed by the live LocalPlayer. */
public final class FabricPlayerHandle implements PlayerHandle {

    private final LocalPlayer player;

    public FabricPlayerHandle(LocalPlayer player) {
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
        return String.valueOf(player.level().dimension().identifier());
    }

    @Override
    public int selectedHotbarSlot() {
        return player.getInventory().getSelectedSlot();
    }
}
