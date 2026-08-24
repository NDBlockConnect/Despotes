package dev.despotes.fabric;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/** Fabric client entrypoint: boots Despotes and wires the client tick hook. */
public final class DespotesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Despotes despotes = Despotes.boot(new FabricPlatform());
        ClientTickEvents.END_CLIENT_TICK.register(client -> despotes.clientTick());

        // v26.11 fix: message events MUST be registered before any registration that
        // could throw — a throw here previously left the event bus silent for the whole
        // session (tick events kept working, which masked the failure).
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, instant) -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("message", message.getString());
            payload.addProperty("kind", "chat");
            payload.addProperty("sender", sender == null ? "" : sender.name());
            despotes.eventBus().publish("chat", payload);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("message", message.getString());
            payload.addProperty("kind", "system");
            payload.addProperty("overlay", overlay);
            despotes.eventBus().publish(overlay ? "overlay" : "system", payload);
        });

        // Overlay rendering via the fabric-api HudElementRegistry (no Hud mixin on 26.1).
        // Defensive: an HUD-hook failure must never take down the control channel.
        try {
            net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                    net.minecraft.resources.Identifier.parse("despotes:overlay"),
                    (graphics, deltaTracker) -> {
                        despotes.frameEnd();
                        HudOverlay.draw(graphics, net.minecraft.client.Minecraft.getInstance().font);
                    });
        } catch (Throwable t) {
            despotes.platform().log("[Despotes] HUD overlay registration failed (overlay disabled): " + t);
        }
    }
}
