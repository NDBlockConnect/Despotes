package dev.despotes.common.focus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Win32 window focus helper using pure reflection (no org.lwjgl import).
 *
 * <p>Minimizing/restoring the game window uses {@code user32!ShowWindow} resolved through
 * JNA when present (best-effort). If JNA is absent every call is a no-op so the feature is
 * strictly optional. The game's GLFW handle is read reflectively from
 * {@code net.minecraft.client.renderer.Window#handle} (private field across versions).
 */
public final class WindowControl {

    private static Object user32;
    private static Method showWindow;
    private static boolean probed;

    private WindowControl() {
    }

    private static boolean available() {
        if (!probed) {
            probed = true;
            try {
                Class<?> u32 = Class.forName("com.sun.jna.platform.win32.User32");
                user32 = u32.getField("INSTANCE").get(null);
                Class<?> hwnd = Class.forName("com.sun.jna.platform.win32.WinDef$HWND");
                showWindow = u32.getMethod("ShowWindow", hwnd, int.class);
            } catch (Throwable t) {
                user32 = null;
            }
        }
        return user32 != null;
    }

    /** Minimize or restore the game window (best-effort, optional JNA). */
    public static void setMinimized(Object minecraftWindow, boolean minimized) {
        if (!available() || minecraftWindow == null) {
            return;
        }
        long handle = readHandle(minecraftWindow);
        if (handle == 0) {
            return;
        }
        try {
            Class<?> hwndCls = Class.forName("com.sun.jna.platform.win32.WinDef$HWND");
            Object hwnd = hwndCls.getConstructor(long.class).newInstance(handle);
            // SW_MINIMIZE = 6, SW_RESTORE = 9
            showWindow.invoke(user32, hwnd, minimized ? 6 : 9);
        } catch (Throwable ignored) {
        }
    }

    private static long readHandle(Object window) {
        try {
            Field f = window.getClass().getDeclaredField("handle");
            f.setAccessible(true);
            return (long) f.get(window);
        } catch (Throwable t) {
            return 0;
        }
    }
}
