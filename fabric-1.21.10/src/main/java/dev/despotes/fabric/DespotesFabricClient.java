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

        // Overlay rendering via the fabric-api HudElementRegistry (no Hud mixin on 1.21.10).
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                net.minecraft.resources.ResourceLocation.parse("despotes:overlay"),
                (graphics, deltaTracker) -> {
                    despotes.frameEnd();
                    HudOverlay.draw(graphics, net.minecraft.client.Minecraft.getInstance().font);
                });

        // Alpha.9: capture inbound chat / system messages into the event bus so
        // callers can poll GET /despotes/v1/events for what the game said.
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
    }
}
