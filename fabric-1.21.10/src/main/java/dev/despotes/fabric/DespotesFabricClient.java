package dev.despotes.fabric;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.despotes.common.Despotes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;

/** Fabric client entrypoint: boots Despotes and wires the client tick hook. */
public final class DespotesFabricClient implements ClientModInitializer {

    /** v26.13-Alpha.1: HUD visibility toggle; default from despotes.json, rebindable in Controls. */
    private static KeyMapping toggleHudKey;

    @Override
    public void onInitializeClient() {
        Despotes despotes = Despotes.boot(new FabricPlatform());

        // v26.13-Alpha.1: HUD toggle key bind. The default key comes from
        // visualization.toggleKey in despotes.json; after the first launch the vanilla
        // Controls screen (options.txt) owns the binding.
        try {
            toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.despotes.toggle_hud",
                    resolveDefaultKey(despotes),
                    KeyMapping.Category.register(
                            net.minecraft.resources.ResourceLocation.parse("despotes:control"))));
        } catch (Throwable t) {
            despotes.platform().log("[Despotes] HUD toggle key bind registration failed: " + t);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            despotes.clientTick();
            if (toggleHudKey != null) {
                while (toggleHudKey.consumeClick()) {
                    despotes.overlay().toggle();
                }
            }
        });

        // Overlay rendering via the fabric-api HudElementRegistry (no Hud mixin on 1.21.10).
        // Defensive: an HUD-hook failure must never take down the control channel.
        try {
            net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                    net.minecraft.resources.ResourceLocation.parse("despotes:overlay"),
                    (graphics, deltaTracker) -> {
                        despotes.frameEnd();
                        HudOverlay.draw(graphics, net.minecraft.client.Minecraft.getInstance().font);
                    });
        } catch (Throwable t) {
            despotes.platform().log("[Despotes] HUD overlay registration failed (overlay disabled): " + t);
        }

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

    /** v26.13-Alpha.1: resolves the configured default key, falling back to F8. */
    private static int resolveDefaultKey(Despotes despotes) {
        try {
            InputConstants.Key key = InputConstants.getKey(despotes.config().visualization.toggleKey);
            if (key != null && key != InputConstants.UNKNOWN) {
                return key.getValue();
            }
        } catch (Throwable t) {
            despotes.platform().log("[Despotes] invalid visualization.toggleKey; falling back to F8");
        }
        return org.lwjgl.glfw.GLFW.GLFW_KEY_F8;
    }
}
