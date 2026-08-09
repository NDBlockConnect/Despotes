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

    /** Set synthetic movement state for the current tick. */
    default void setMovement(double forward, double left, boolean jump, boolean sneak, boolean sprint) {
    }

    /** Set the player view rotation (absolute yaw/pitch). */
    default void setRotation(float yaw, float pitch) {
    }

    /** Select a hotbar slot (0-8). */
    default void selectHotbarSlot(int slot) {
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
}
