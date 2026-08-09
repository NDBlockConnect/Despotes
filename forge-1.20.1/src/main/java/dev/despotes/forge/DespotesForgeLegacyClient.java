package dev.despotes.forge;

import dev.despotes.common.Despotes;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

@Mod("despotes")
public final class DespotesForgeLegacyClient {

    private static volatile boolean booted;

    public DespotesForgeLegacyClient(IEventBus modBus) {
        modBus.addListener((RenderGuiOverlayEvent.Post e) -> {
            Despotes d = bootOnce();
            if (d != null) {
                d.frameEnd();
                ForgeGuiOverlay.draw(d, e.getGuiGraphics());
            }
        });
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
            synchronized (DespotesForgeLegacyClient.class) {
                if (!booted) {
                    Despotes.boot(new LegacyForgePlatform());
                    booted = true;
                }
            }
        }
        return Despotes.get();
    }
}
