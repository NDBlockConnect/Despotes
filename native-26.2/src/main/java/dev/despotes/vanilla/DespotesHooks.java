package dev.despotes.vanilla;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;

import java.lang.reflect.Method;

/**
 * Static targets of the ASM instrumentation (v26.2-Alpha.3). The instrumented bytecode in
 * {@link DespotesTransformer} calls these methods; everything here must be defensive —
 * an exception escaping into the game's tick or packet path would crash the client.
 */
public final class DespotesHooks {

    /** Set once the tick hook fires; the legacy pump stops double-driving. */
    private static volatile boolean hookActive;
    private static volatile boolean announced;

    private DespotesHooks() {
    }

    /** True once the ASM tick hook has taken over driving the control core. */
    public static boolean hookActive() {
        return hookActive;
    }

    /** Instrumented at every normal exit of {@code Minecraft.tick()V}. Runs on the client thread. */
    public static void onClientTick() {
        try {
            hookActive = true;
            Despotes d = Despotes.get();
            if (d == null) {
                d = Despotes.boot(new NativePlatform());
            }
            if (!announced) {
                announced = true;
                d.platform().log("[Despotes] tick hook active — driven by the game loop (ASM).");
            }
            d.clientTick();
            d.frameEnd();
        } catch (Throwable ignored) {
            // Never propagate into the game tick.
        }
    }

    /** Instrumented at entry of {@code ClientPacketListener.handleSystemChat}. */
    public static void onSystemChat(Object packet) {
        try {
            Despotes d = Despotes.get();
            if (d == null || packet == null) {
                return;
            }
            Object content = call(packet, "content");
            boolean overlay = callBool(packet, "overlay");
            JsonObject payload = new JsonObject();
            payload.addProperty("message", text(content));
            payload.addProperty("kind", "system");
            payload.addProperty("overlay", overlay);
            d.eventBus().publish(overlay ? "overlay" : "system", payload);
        } catch (Throwable ignored) {
        }
    }

    /** Instrumented at entry of {@code ClientPacketListener.handlePlayerChat}. */
    public static void onPlayerChat(Object packet) {
        try {
            Despotes d = Despotes.get();
            if (d == null || packet == null) {
                return;
            }
            String message = "";
            Object body = call(packet, "body");
            if (body != null) {
                message = text(call(body, "content"));
            }
            if (message.isEmpty()) {
                message = text(call(packet, "unsignedContent"));
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("message", message);
            payload.addProperty("kind", "chat");
            payload.addProperty("sender", String.valueOf(call(packet, "sender")));
            d.eventBus().publish("chat", payload);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Instrumented at entry of {@code ClientPacketListener.handleDisguisedChat}. Offline /
     * unsigned sessions deliver player chat as disguised chat, so this path is what the
     * local test client actually uses.
     */
    public static void onDisguisedChat(Object packet) {
        try {
            Despotes d = Despotes.get();
            if (d == null || packet == null) {
                return;
            }
            JsonObject payload = new JsonObject();
            // The disguised chat packet is a record: the accessor is message(), not content().
            payload.addProperty("message", text(call(packet, "message")));
            payload.addProperty("kind", "chat");
            payload.addProperty("sender", "");
            payload.addProperty("disguised", true);
            d.eventBus().publish("chat", payload);
        } catch (Throwable ignored) {
        }
    }

    private static Object call(Object o, String method) {
        try {
            Method m = o.getClass().getMethod(method);
            return m.invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean callBool(Object o, String method) {
        Object v = call(o, method);
        return v instanceof Boolean b && b;
    }

    /** Component-like objects expose getString(); fall back to toString. */
    private static String text(Object component) {
        if (component == null) {
            return "";
        }
        try {
            return String.valueOf(component.getClass().getMethod("getString").invoke(component));
        } catch (Throwable t) {
            return String.valueOf(component);
        }
    }
}
