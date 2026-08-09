package dev.despotes.forge;

import dev.despotes.common.Despotes;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge client entrypoint for the legacy official-mapping range (Minecraft 1.21.1).
 * Boot is deferred until the Minecraft instance exists.
 */
@Mod("despotes")
public final class DespotesForgeClient {

    private static volatile boolean booted;

    public DespotesForgeClient(IEventBus modBus) {
        modBus.addListener((AddGuiOverlayLayersEvent e) ->
                e.getLayeredDraw().add(ForgeHudOverlay.LAYER));

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post e) -> {
            Despotes d = bootOnce();
            if (d != null) {
                d.clientTick();
            }
        });
    }

    private static Despotes bootOnce() {
        if (Minecraft.getInstance() == null) {
            return null;
        }
        if (!booted) {
            synchronized (DespotesForgeClient.class) {
                if (!booted) {
                    Despotes.boot(new ForgePlatform());
                    booted = true;
                }
            }
        }
        return Despotes.get();
    }
}
