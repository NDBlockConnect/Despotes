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
     * Capture the framebuffer synchronously on the calling thread (which must be the
     * client thread). Reads the most recently rendered frame.
     */
    ShotHandle captureFrame(ScreenshotOptions options);

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
}
