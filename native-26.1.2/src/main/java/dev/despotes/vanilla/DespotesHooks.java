package dev.despotes.vanilla;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;

import java.lang.reflect.Method;

/**
 * Static targets of the ASM instrumentation (v26.2-Alpha.3, loader-aware in Alpha.4). The
 * instrumented bytecode in {@link DespotesTransformer} calls these methods; everything here
 * must be defensive — an exception escaping into the game's tick or packet path would crash
 * the client.
 *
 * <p>v26.2-Alpha.4 — JavaAgent For All (loader mixing). When the agent jar is attached to a
 * process that ALSO runs a mod loader (fabric / neoforge / forge / aprism) with the matching
 * Despotes mod installed, the mod line's {@code Despotes} class lives in the mod loader's
 * classloader — a <em>different class</em> from the agent's. {@link #probeOwnership(Object)}
 * resolves this through the game's own classloader so both the tick hook and the legacy pump
 * know who owns the control core and never double-drive.
 */
public final class DespotesHooks {

    /** Ownership verdicts from {@link #probeOwnership(Object)}. */
    public static final String OWNER_NATIVE = "native";
    /** A mod loader owns the core; the agent must stand by. */
    public static final String OWNER_FOREIGN = "foreign";

    /** Set once the ASM tick hook drives the core (native mode only). */
    private static volatile boolean hookActive;
    /** The loader id that owns the core in companion mode, e.g. "fabric". */
    private static volatile String companionLoader;
    private static volatile boolean announced;

    private DespotesHooks() {
    }

    /** True once the ASM tick hook has taken over driving the control core. */
    public static boolean hookActive() {
        return hookActive;
    }

    /** Non-null when a mod loader owns the core and the agent stands by. */
    public static String companionLoader() {
        return companionLoader;
    }

    /**
     * Resolve who owns the Despotes control core from the game's viewpoint.
     *
     * @param mc the live Minecraft instance (its classloader is the game's)
     * @return {@link #OWNER_NATIVE} when the agent may own and drive the core;
     *         {@link #OWNER_FOREIGN} when a foreign classloader owns the game and no mod
     *         core is present (agent must not boot); a mod loader id ("fabric", ...) when
     *         a mod copy already booted the core; {@code null} when a mod copy exists but
     *         has not booted yet (caller should wait and re-probe).
     */
    public static String probeOwnership(Object mc) {
        try {
            ClassLoader gameCl = mc.getClass().getClassLoader();
            Class<?> despotesClass = Class.forName("dev.despotes.common.Despotes", false, gameCl);
            if (despotesClass == Despotes.class) {
                // Same class object: the game runs in the agent's own classloader — no mod copy.
                return OWNER_NATIVE;
            }
            Object core = despotesClass.getMethod("get").invoke(null);
            if (core == null) {
                // A mod copy exists but has not booted yet.
                return null;
            }
            Object platform = core.getClass().getMethod("platform").invoke(core);
            String id = String.valueOf(platform.getClass().getMethod("loaderId").invoke(platform));
            return "native".equals(id) ? OWNER_NATIVE : id;
        } catch (ClassNotFoundException e) {
            // The game's classloader cannot see any Despotes core. If the game class itself
            // lives in our classloader we may own it; otherwise a foreign loader (e.g. Knot
            // without the mod) owns the game and we must not boot direct-reference code.
            try {
                return mc.getClass().getClassLoader() == Despotes.class.getClassLoader()
                        ? OWNER_NATIVE : OWNER_FOREIGN;
            } catch (Throwable t) {
                return OWNER_FOREIGN;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** Instrumented at every normal exit of {@code Minecraft.tick()V}. Runs on the client thread. */
    public static void onClientTick(Object mc) {
        try {
            if (companionLoader == null) {
                String owner = probeOwnership(mc);
                if (owner != null && !OWNER_NATIVE.equals(owner)) {
                    companionLoader = owner;
                    if (!announced) {
                        announced = true;
                        System.out.println("[Despotes] agent companion mode — '"
                                + owner + "' owns the control core; agent stands by.");
                    }
                    return;
                }
            }
            if (companionLoader != null) {
                return;
            }
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

    /** Publish chat/system events only when the agent owns the core (native mode). */
    private static boolean shouldPublish() {
        Despotes d = Despotes.get();
        return d != null && "native".equals(d.platform().loaderId());
    }

    /** Instrumented at entry of {@code ClientPacketListener.handleSystemChat}. */
    public static void onSystemChat(Object packet) {
        try {
            if (!shouldPublish() || companionLoader != null) {
                return;
            }
            Despotes d = Despotes.get();
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
            if (!shouldPublish() || companionLoader != null) {
                return;
            }
            Despotes d = Despotes.get();
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
            if (!shouldPublish() || companionLoader != null) {
                return;
            }
            Despotes d = Despotes.get();
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
