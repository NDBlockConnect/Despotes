package dev.despotes.neoforge;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client entrypoint for Minecraft 26.2 (official mappings).
 *
 * <p>NeoForge constructs {@code @Mod} classes very early — before the {@link Minecraft}
 * instance exists. Since {@link Despotes#boot} reads the game directory from the running
 * client, boot is deferred until the first client tick (by which time the client is fully
 * initialised).
 */
@Mod(value = "despotes", dist = Dist.CLIENT)
public final class DespotesNeoForgeClient {

    private static volatile boolean booted;

    public DespotesNeoForgeClient() {
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
                HudOverlay.draw(e.getGuiGraphics(), Minecraft.getInstance().font);
            }
        });

        // v26.2-Alpha.8 (loader parity): capture inbound chat / system messages
        // into the event bus, matching the fabric line so /events works everywhere.
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.Player e) -> {
            Despotes d = bootOnce();
            if (d == null) {
                return;
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("message", e.getMessage().getString());
            payload.addProperty("kind", "chat");
            payload.addProperty("sender", String.valueOf(e.getSender()));
            d.eventBus().publish("chat", payload);
        });
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.System e) -> {
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

    /** Boots Despotes lazily once the Minecraft instance exists; returns the runtime. */
    private static Despotes bootOnce() {
        if (Minecraft.getInstance() == null) {
            return null;
        }
        if (!booted) {
            synchronized (DespotesNeoForgeClient.class) {
                if (!booted) {
                    Despotes.boot(new NeoForgePlatform());
                    booted = true;
                }
            }
        }
        return Despotes.get();
    }
}
