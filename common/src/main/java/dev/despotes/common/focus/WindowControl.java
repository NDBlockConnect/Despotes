package dev.despotes.common.focus;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * OS window focus control that never grabs the user's focus.
 *
 * <p>Minimizing/restoring is done through GLFW so it is version-tolerant: 26.x exposes the
 * raw {@code handle} as a private field, older versions expose {@code getWindow()}.
 */
public final class WindowControl {

    private WindowControl() {
    }

    public static void setMinimized(Window window, boolean minimized) {
        long handle = handle(window);
        if (handle == 0) {
            return;
        }
        if (minimized) {
            GLFW.glfwIconifyWindow(handle);
        } else {
            GLFW.glfwRestoreWindow(handle);
        }
    }

    private static long handle(Window w) {
        try {
            Method m = Window.class.getMethod("getWindow");
            return (long) m.invoke(w);
        } catch (Exception ignored) {
        }
        try {
            Field f = Window.class.getDeclaredField("handle");
            f.setAccessible(true);
            return (long) f.get(w);
        } catch (Exception ignored) {
        }
        return 0;
    }
}
