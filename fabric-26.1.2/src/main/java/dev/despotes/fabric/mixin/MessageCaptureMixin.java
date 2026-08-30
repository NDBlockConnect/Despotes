package dev.despotes.fabric.mixin;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v26.11 fix: fabric-api 0.155.2 (26.1.2) ships message-api with server-side mixins
 * only — ClientReceiveMessageEvents never fire on this line. We capture inbound chat
 * at the packet-handler level ourselves and publish onto the Despotes event bus.
 *
 * 26.1.2 API notes: ClientboundPlayerChatPacket exposes {@code unsignedContent()}
 * (null when the signed body carries the content); ChatType$Bound has no overlay()
 * accessor on this version, so disguised chat is published as non-overlay chat.
 */
@Mixin(ClientPacketListener.class)
public abstract class MessageCaptureMixin {

    static {
        org.slf4j.LoggerFactory.getLogger("Despotes").info("[Despotes] MessageCaptureMixin class loaded");
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void despotes$onInit(CallbackInfo ci) {
        org.slf4j.LoggerFactory.getLogger("Despotes").info("[Despotes] MessageCaptureMixin: ClientPacketListener instantiated");
    }

    private static void despotes$publish(String type, Component message, boolean overlay) {
        Despotes d = Despotes.get();
        if (d == null) {
            org.slf4j.LoggerFactory.getLogger("Despotes").warn("[Despotes] publish skipped: Despotes instance null");
            return;
        }
        if (message == null) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message.getString());
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        payload.addProperty("kind", type);
        payload.addProperty("overlay", overlay);
        d.eventBus().publish(type, payload);
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void despotes$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        despotes$publish(packet.overlay() ? "overlay" : "system", packet.content(), packet.overlay());
    }

    @Inject(method = "handlePlayerChat", at = @At("HEAD"))
    private void despotes$onPlayerChat(net.minecraft.network.protocol.game.ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        despotes$publish("chat", packet.unsignedContent(), false);
    }

    @Inject(method = "handleDisguisedChat", at = @At("HEAD"))
    private void despotes$onDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
        despotes$publish("chat", packet.message(), false);
    }
}
