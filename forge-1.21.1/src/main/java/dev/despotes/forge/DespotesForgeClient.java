package dev.despotes.forge;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
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

    /** v26.13-Alpha.1: HUD visibility toggle (default F8, rebindable in Controls). */
    private static KeyMapping toggleHudKey;

    public DespotesForgeClient(IEventBus modBus) {
        // v26.13-Alpha.1: HUD toggle key bind on the mod event bus. The default key is
        // F8; after the first launch the vanilla Controls screen owns the binding.
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            toggleHudKey = new KeyMapping("key.despotes.toggle_hud",
                    org.lwjgl.glfw.GLFW.GLFW_KEY_F8, "key.categories.despotes");
            e.register(toggleHudKey);
        });

        modBus.addListener((AddGuiOverlayLayersEvent e) ->
                e.getLayeredDraw().add(ForgeHudOverlay.LAYER));

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post e) -> {
            Despotes d = bootOnce();
            if (d != null) {
                d.clientTick();
                if (toggleHudKey != null) {
                    while (toggleHudKey.consumeClick()) {
                        d.overlay().toggle();
                    }
                }
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
            payload.addProperty("sender", String.valueOf(e.getSender()));
            d.eventBus().publish("chat", payload);
        });
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
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
