package dev.despotes.forge;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
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
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Despotes d = bootOnce();
            if (d != null) {
                d.clientTick();
            }
        });

        // v26.2-Alpha.8 (loader parity): capture inbound chat / system messages
        // into the event bus, matching the fabric line so /events works everywhere.
        MinecraftForge.EVENT_BUS.addListener((ClientChatReceivedEvent.Player e) -> {
            Despotes d = bootOnce();
            if (d == null) {
                return;
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("message", e.getMessage().getString());
            payload.addProperty("kind", "chat");
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
            payload.addProperty("sender", String.valueOf(e.getSender()));
            d.eventBus().publish("chat", payload);
        });
        MinecraftForge.EVENT_BUS.addListener((ClientChatReceivedEvent.System e) -> {
            Despotes d = bootOnce();
            if (d == null) {
                return;
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("message", e.getMessage().getString());
            payload.addProperty("kind", "system");
            payload.addProperty("overlay", e.isOverlay());
            d.eventBus().publish(e.isOverlay() ? "overlay" : "system", payload);
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
