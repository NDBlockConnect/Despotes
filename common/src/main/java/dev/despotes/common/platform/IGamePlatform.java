package dev.despotes.common.platform;

import dev.despotes.common.action.ScreenshotOptions;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Platform abstraction. Every loader branch implements this once; the common core only
 * ever talks to this interface, never to Minecraft classes directly.
 *
 * <h2>Threading contract</h2>
 * Minecraft runs game logic and rendering on a single main thread. The common core calls
 * these methods from <em>transport worker threads</em> (HTTP workers, CLI reader, FileDrop
 * worker). To touch game state safely, a worker thread hops onto the client thread with
 * {@link #awaitOnClientThread(Supplier, long)}, which blocks the caller until the client
 * thread runs the task (or times out). Implementations must never block the client thread
 * waiting on itself.
 *
 * <p>Frame capture is synchronous on the calling (client) thread and reads the most
 * recently rendered framebuffer, so it works while the window is unfocused.
 */
public interface IGamePlatform {

    /** Loader identifier: "native" | "fabric" | "neoforge" | "forge" | "aprism". */
    String loaderId();

    /** Minecraft version string, e.g. "26.2", "1.21.4". */
    String mcVersion();

    /** Game directory (contains options.txt). */
    Path gameDir();

    /** Run on the client (main game) thread, next tick. Fire-and-forget. */
    void scheduleOnClientThread(Runnable r);

    /**
     * Run a supplier on the client thread and block the calling thread until it returns,
     * up to {@code timeoutMs}. Returns null on timeout. This is the single safe bridge
     * from transport threads into game state.
     */
    <T> T awaitOnClientThread(Supplier<T> task, long timeoutMs);

    /**
     * Begin an asynchronous framebuffer capture. The submission happens on the client
     * thread; {@code done} is invoked (possibly on another thread) with the captured
     * shot, or null on failure/timeout. Callers must not block the client thread while
     * waiting — the dispatcher parks the command's response future instead.
     */
    void beginCapture(ScreenshotOptions options, java.util.function.Consumer<ShotHandle> done);

    /** True when a client world is loaded and the player exists. */
    boolean inGame();

    /** Current player handle, or null. */
    PlayerHandle player();

    /** Current open screen handle, or null when no GUI is open. */
    ScreenHandle screen();

    /** Current FPS (or -1 when unknown). */
    int fps();

    /** Whether the OS window currently has keyboard focus. */
    boolean windowFocused();

    /** Mod loader log. */
    void log(String line);

    /** Resolve a key binding name ("key.keyboard.w") to the platform key value. */
    default int keyIdFor(String keyName) {
        return -1;
    }

    /** Inject a synthetic key press/release into the game's key handlers. */
    default void injectKey(String keyName, boolean pressed) {
    }

    /** Inject a mouse click at GUI coordinates on the current screen. */
    default void injectMouseClick(double x, double y, int button, boolean pressed, boolean shift) {
    }

    /** Inject typed characters into the focused text field / chat. */
    default void injectChars(String text) {
    }

    /** Open the chat screen (if not open). */
    default void openChat() {
    }

    /** Send a chat message / command as the player. */
    default void sendChat(String text) {
    }

    /**
     * Send a slash command (with or without the leading '/') through the command channel.
     * v26.2-Alpha.1 fix: previously a command was pushed through {@link #sendChat(String)},
     * which signs it as a chat message, so the server never executed it. This default
     * reflectively resolves {@code ClientPacketListener.sendCommand(String)} (present under
     * the same official name on every supported MC version) so no loader line needs a change;
     * it falls back to {@link #sendChat(String)} only if the command channel is unavailable.
     */
    default void sendCommand(String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            Object conn = mc.getClass().getMethod("getConnection").invoke(mc);
            if (conn != null) {
                try {
                    conn.getClass().getMethod("sendCommand", String.class).invoke(conn, cmd);
                    return;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        sendChat("/" + cmd);
    }

    /** Set synthetic movement state for the current tick. */
    default void setMovement(double forward, double left, boolean jump, boolean sneak, boolean sprint) {
    }

    /** Set the player view rotation (absolute yaw/pitch). */
    default void setRotation(float yaw, float pitch) {
    }

    /** Select a hotbar slot (0-8). Reflective across versions. */
    default void selectHotbarSlot(int slot) {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            Object player = mc.getClass().getField("player").get(mc);
            if (player == null) {
                return;
            }
            Object inv = player.getClass().getMethod("getInventory").invoke(player);
            try {
                inv.getClass().getMethod("setSelectedSlot", int.class).invoke(inv, slot);
            } catch (NoSuchMethodException e) {
                java.lang.reflect.Field f = inv.getClass().getField("selected");
                f.setInt(inv, slot);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * v26.3-Alpha.1: click a slot in the currently open container menu.
     *
     * <p>This is the universal entry point for all inventory manipulation: moving items,
     * dropping, splitting, swapping, quick-moving (shift-click). It works reflectively across
     * all supported MC versions:
     * <ul>
     *   <li><b>26.x</b>: {@code MultiPlayerGameMode.handleContainerInput(int slot, int button,
     *       int clickCount, ContainerInput, Player)} where {@code ContainerInput} is an enum
     *       (PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL).</li>
     *   <li><b>1.20.x / 1.21.x</b>: {@code MultiPlayerGameMode.handleInventoryMouseClick(int
     *       containerId, int slot, int button, ClickType, Player)} where {@code ClickType}
     *       is an enum (PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, PICKUP_ALL).</li>
     * </ul>
     *
     * @param slotIndex   the slot index in the open menu (0-based)
     * @param button      mouse button (0=left, 1=right)
     * @param clickType   one of: "pickup", "quick_move", "swap", "clone", "throw", "pickup_all"
     * @param clickCount  click count (for double-click etc; usually 0)
     * @return true if the click was dispatched
     */
    default boolean slotClick(int slotIndex, int button, String clickType, int clickCount) {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            // Resolve gameMode field — named "gameMode" across versions
            java.lang.reflect.Field gmField = null;
            Class<?> mcCls = mc.getClass();
            while (mcCls != null) {
                try {
                    gmField = mcCls.getDeclaredField("gameMode");
                    break;
                } catch (NoSuchFieldException e) {
                    mcCls = mcCls.getSuperclass();
                }
            }
            if (gmField == null) return false;
            gmField.setAccessible(true);
            Object gm = gmField.get(mc);
            if (gm == null) return false;
            Object player = mc.getClass().getField("player").get(mc);
            if (player == null) return false;
            // Resolve Player superclass for method lookup (methods declare Player, not LocalPlayer)
            Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
            String enumName = clickType.toUpperCase().replace("-", "_");

            // Try 26.x API: handleContainerInput(int containerId, int slot, int button, ContainerInput, Player)
            try {
                Class<?> inputEnum = Class.forName("net.minecraft.world.inventory.ContainerInput");
                Object inputVal = java.lang.Enum.valueOf(
                        inputEnum.asSubclass(java.lang.Enum.class), enumName);
                Object menu = player.getClass().getField("containerMenu").get(player);
                int containerId = menu.getClass().getField("containerId").getInt(menu);
                java.lang.reflect.Method m = gm.getClass().getMethod(
                        "handleContainerInput", int.class, int.class, int.class, inputEnum, playerClass);
                m.invoke(gm, containerId, slotIndex, button, inputVal, player);
                return true;
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                // Fall through to legacy API
            }

            // Try 1.20.x / 1.21.x API: handleInventoryMouseClick(int containerId, int slot, int button, ClickType, Player)
            try {
                Class<?> clickTypeEnum = Class.forName("net.minecraft.world.inventory.ClickType");
                Object clickVal = java.lang.Enum.valueOf(
                        clickTypeEnum.asSubclass(java.lang.Enum.class), enumName);
                Object menu = player.getClass().getField("containerMenu").get(player);
                int containerId = menu.getClass().getField("containerId").getInt(menu);
                java.lang.reflect.Method m = gm.getClass().getMethod(
                        "handleInventoryMouseClick", int.class, int.class, int.class, clickTypeEnum, playerClass);
                m.invoke(gm, containerId, slotIndex, button, clickVal, player);
                return true;
            } catch (NoSuchMethodException | ClassNotFoundException e2) {
                // Final fallback: call AbstractContainerMenu.clicked directly
                Object menu = player.getClass().getField("containerMenu").get(player);
                try {
                    Class<?> inputEnum = Class.forName("net.minecraft.world.inventory.ContainerInput");
                    Object inputVal = java.lang.Enum.valueOf(
                            inputEnum.asSubclass(java.lang.Enum.class), enumName);
                    menu.getClass().getMethod("clicked", int.class, int.class, inputEnum, playerClass)
                            .invoke(menu, slotIndex, button, inputVal, player);
                    return true;
                } catch (ClassNotFoundException e3) {
                    Class<?> clickTypeEnum = Class.forName("net.minecraft.world.inventory.ClickType");
                    Object clickVal = java.lang.Enum.valueOf(
                            clickTypeEnum.asSubclass(java.lang.Enum.class), enumName);
                    menu.getClass().getMethod("clicked", int.class, int.class, clickTypeEnum, playerClass)
                            .invoke(menu, slotIndex, button, clickVal, player);
                    return true;
                }
            }
        } catch (Throwable t) {
            log("[Despotes] slotClick failed: " + t);
            return false;
        }
    }

    /** World interactions. */
    default void worldAttack() {
    }

    default void worldUseItem(String hand) {
    }

    default void worldPlaceBlock(int x, int y, int z, String face, String hand) {
    }

    default void worldDropItem(boolean stack) {
    }

    default void worldPickBlock() {
    }

    /** Draw a HUD overlay line batch (called on the render thread after GUI draw). */
    default void drawOverlay(java.util.List<String> lines) {
    }

    /**
     * Release the captured (grabbed) mouse cursor back to the OS desktop. Used so the game
     * does not keep locking the user's mouse while the window is unfocused. No-op when the
     * cursor is not captured.
     */
    default void releaseMouseCapture() {
    }

    /**
     * Grab (capture) the mouse cursor for first-person camera control. No-op when a screen
     * is open or no world is loaded.
     */
    default void grabMouseCapture() {
    }

    /** True when the game currently has the mouse cursor captured (grabbed). */
    default boolean isMouseCaptured() {
        return false;
    }

    /**
     * Enable or disable the vanilla "pause the game when the window loses focus"
     * behaviour. External control only works while the window is unfocused, so Despotes
     * disables this by default (see {@code focus.preventPauseOnFocusLoss}). The setting is
     * applied in memory only; implementations should not persist it to user options.
     */
    default void setPauseOnLostFocus(boolean enabled) {
    }

    /** Set the OS window minimized state (used to yield focus on start). */
    default void setWindowMinimized(boolean minimized) {
    }

    /**
     * Run a semantic function action (F5-class keys, issue 4). Returns true when handled.
     * Names: "toggle-perspective" | "toggle-debug" | "toggle-fullscreen" | "toggle-hide-gui"
     * | "open-inventory" | "screenshot-save" | "reload-resources".
     */
    default boolean runFunction(String fn) {
        return false;
    }

    /** World summary (data-level perception): biome, time, difficulty, seed... */
    default com.google.gson.JsonObject probeWorld() {
        return new com.google.gson.JsonObject();
    }

    /** Block data snapshot around a centre, radius-limited. */
    default com.google.gson.JsonObject probeBlocks(int x, int y, int z, int r) {
        return new com.google.gson.JsonObject();
    }

    /** Nearby entities within radius, nearest-first, capped. */
    default com.google.gson.JsonObject probeEntities(double radius) {
        return new com.google.gson.JsonObject();
    }

    /** What the crosshair currently targets (block or entity) with distance. */
    default com.google.gson.JsonObject probeTarget() {
        return new com.google.gson.JsonObject();
    }

    /**
     * v26.2-Alpha.7: locate a loaded entity by UUID for the {@code look} action's
     * {@code lookat} mode. Reflective across versions: prefers
     * {@code Level#getEntity(UUID)} (26.x / 1.21.x) and falls back to the level's entity
     * getter ({@code getEntities().get(UUID)}) on older lines. Returns
     * {@code {"found":false}} when the entity is not loaded.
     */
    default com.google.gson.JsonObject findEntity(String uuid) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        try {
            java.util.UUID id = java.util.UUID.fromString(uuid);
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            Object level = mc.getClass().getField("level").get(mc);
            Object entity = null;
            try {
                entity = level.getClass().getMethod("getEntity", java.util.UUID.class).invoke(level, id);
            } catch (NoSuchMethodException ignored) {
                java.lang.reflect.Method getter = level.getClass().getDeclaredMethod("getEntities");
                getter.setAccessible(true);
                entity = getter.invoke(level).getClass()
                        .getMethod("get", java.util.UUID.class).invoke(null, id);
            }
            if (entity == null) {
                o.addProperty("found", false);
                return o;
            }
            o.addProperty("found", true);
            o.addProperty("x", ((Number) entity.getClass().getMethod("getX").invoke(entity)).doubleValue());
            o.addProperty("y", ((Number) entity.getClass().getMethod("getY").invoke(entity)).doubleValue());
            o.addProperty("z", ((Number) entity.getClass().getMethod("getZ").invoke(entity)).doubleValue());
            return o;
        } catch (Throwable t) {
            o.addProperty("found", false);
            return o;
        }
    }

    /** v26.2-Alpha.2 extended self vitals: food/armor/air, state flags, motion, effects, environment. */
    default com.google.gson.JsonObject probeSelf() {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            return dev.despotes.common.probe.WorldProbes.self(
                    (net.minecraft.client.Minecraft) mc);
        } catch (Throwable t) {
            return new com.google.gson.JsonObject();
        }
    }

    /** v26.2-Alpha.2 threat awareness: hostile mobs + projectiles within radius. */
    default com.google.gson.JsonObject probeThreats(double radius) {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            return dev.despotes.common.probe.WorldProbes.threats(
                    (net.minecraft.client.Minecraft) mc, radius);
        } catch (Throwable t) {
            return new com.google.gson.JsonObject();
        }
    }

    /** Open container menu snapshot (slots + counts). Reflective MC access. */
    default com.google.gson.JsonObject probeContainer() {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            return dev.despotes.common.probe.WorldProbes.container(
                    (net.minecraft.client.Minecraft) mc);
        } catch (Throwable t) {
            return new com.google.gson.JsonObject();
        }
    }

    /** v26.3-Alpha.2: recipe book readout (known + highlighted recipes). */
    default com.google.gson.JsonObject probeRecipes() {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            return dev.despotes.common.probe.WorldProbes.recipes(
                    (net.minecraft.client.Minecraft) mc);
        } catch (Throwable t) {
            return new com.google.gson.JsonObject();
        }
    }

    /** v26.4-Alpha.1: nearby players query. */
    default com.google.gson.JsonObject probePlayers(double radius) {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            return dev.despotes.common.probe.WorldProbes.players(
                    (net.minecraft.client.Minecraft) mc, radius);
        } catch (Throwable t) {
            return new com.google.gson.JsonObject();
        }
    }

    /** v26.4-Alpha.2: server info query. */
    default com.google.gson.JsonObject probeServer() {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            return dev.despotes.common.probe.WorldProbes.server(
                    (net.minecraft.client.Minecraft) mc);
        } catch (Throwable t) {
            return new com.google.gson.JsonObject();
        }
    }

    /** v26.4-Alpha.3: tablist query. */
    default com.google.gson.JsonObject probeTablist() {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            return dev.despotes.common.probe.WorldProbes.tablist(
                    (net.minecraft.client.Minecraft) mc);
        } catch (Throwable t) {
            return new com.google.gson.JsonObject();
        }
    }

    /** v26.4-Alpha.4: scoreboard query. */
    default com.google.gson.JsonObject probeScoreboard() {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            return dev.despotes.common.probe.WorldProbes.scoreboard(
                    (net.minecraft.client.Minecraft) mc);
        } catch (Throwable t) {
            return new com.google.gson.JsonObject();
        }
    }

    /** v26.4-Alpha.7: coords query (spawn, world border, player position). */
    default com.google.gson.JsonObject probeCoords() {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            return dev.despotes.common.probe.WorldProbes.coords(
                    (net.minecraft.client.Minecraft) mc);
        } catch (Throwable t) {
            return new com.google.gson.JsonObject();
        }
    }

    /**
     * v26.2 death awareness: respawn the dead player. Reflective across versions —
     * {@code LocalPlayer.respawn()} exists under the same official name on every supported
     * MC version (1.20.1 → 26.2). Also closes the death screen afterwards
     * ({@code setScreenAndShow(null)} on 26.x, {@code setScreen(null)} on older).
     * Must be called on the client thread. Returns true when the respawn was dispatched.
     */
    default boolean respawn() {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            Object player = mc.getClass().getField("player").get(mc);
            if (player == null) {
                return false;
            }
            player.getClass().getMethod("respawn").invoke(player);
            closeScreen(mc);
            return true;
        } catch (Throwable t) {
            log("[Despotes] respawn failed: " + t);
            return false;
        }
    }

    /** v26.2 death awareness: true when the death screen is currently open. */
    default boolean deathScreenOpen() {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            Object screen = currentScreen(mc);
            if (screen == null) {
                return false;
            }
            Class<?> death = Class.forName("net.minecraft.client.gui.screens.DeathScreen");
            return death.isInstance(screen);
        } catch (Throwable t) {
            return false;
        }
    }

    /** v26.9-Alpha.1: redstone signal query at a block position. */
    default com.google.gson.JsonObject probeRedstone(int x, int y, int z) {
        try {
            Object mc = Class.forName("net.minecraft.client.Minecraft")
                    .getMethod("getInstance").invoke(null);
            return dev.despotes.common.probe.WorldProbes.redstone(
                    (net.minecraft.client.Minecraft) mc, x, y, z);
        } catch (Throwable t) {
            return new com.google.gson.JsonObject();
        }
    }

    /** Current open screen object via the gui helper, or null. Reflective across versions. */
    private static Object currentScreen(Object mc) {
        try {
            // 26.x: mc.gui.screen() ; older: mc.screen field
            Object gui = mc.getClass().getField("gui").get(mc);
            return gui.getClass().getMethod("screen").invoke(gui);
        } catch (Throwable t) {
            try {
                return mc.getClass().getField("screen").get(mc);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    /** Close the current screen (null target) reflectively across versions. */
    private static void closeScreen(Object mc) {
        try {
            // 26.x renamed setScreen -> setScreenAndShow
            try {
                mc.getClass().getMethod("setScreenAndShow",
                        Class.forName("net.minecraft.client.gui.screens.Screen")).invoke(mc, (Object) null);
            } catch (NoSuchMethodException e) {
                mc.getClass().getMethod("setScreen",
                        Class.forName("net.minecraft.client.gui.screens.Screen")).invoke(mc, (Object) null);
            }
        } catch (Throwable ignored) {
        }
    }
}
