package dev.despotes.neoforge;

import dev.despotes.common.Despotes;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client entrypoint for the legacy official-mapping range (Minecraft 1.21.1).
 * Boot is deferred until the Minecraft instance exists.
 */
@Mod(value = "despotes", dist = Dist.CLIENT)
public final class DespotesNeoForgeLegacyClient {

    private static volatile boolean booted;

    public DespotesNeoForgeLegacyClient() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Despotes d = bootOnce();
            if (d != null) {
                d.clientTick();
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> {
            Despotes d = bootOnce();
            if (d != null) {
                d.frameEnd();
                LegacyHudOverlay.draw(d, e.getGuiGraphics());
            }
        });
    }

    private static Despotes bootOnce() {
        if (Minecraft.getInstance() == null) {
            return null;
        }
        if (!booted) {
            synchronized (DespotesNeoForgeLegacyClient.class) {
                if (!booted) {
                    Despotes.boot(new LegacyNeoForgePlatform());
                    booted = true;
                }
            }
        }
        return Despotes.get();
    }
}
