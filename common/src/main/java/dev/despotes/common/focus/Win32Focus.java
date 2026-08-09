package dev.despotes.common.focus;

import java.lang.reflect.Method;

/**
 * Best-effort Win32 foreground-window helper via JNA reflection.
 *
 * <p>JNA is not bundled; if it is present on the classpath (some launchers provide it) the
 * helper uses it to detect/return OS focus. Otherwise every call degrades to a no-op so the
 * feature is strictly optional.
 */
public final class Win32Focus {

    private static Object user32;
    private static Method getForegroundWindow;
    private static Method setForegroundWindow;
    private static boolean probed;

    private Win32Focus() {
    }

    public static boolean available() {
        if (!probed) {
            probed = true;
            try {
                Class<?> nativeCls = Class.forName("com.sun.jna.Native");
                Class<?> user32Cls = Class.forName("com.sun.jna.platform.win32.User32");
                user32 = user32Cls.getField("INSTANCE").get(null);
                getForegroundWindow = user32Cls.getMethod("GetForegroundWindow");
                setForegroundWindow = user32Cls.getMethod("SetForegroundWindow",
                        Class.forName("com.sun.jna.platform.win32.WinDef$HWND"));
            } catch (Throwable t) {
                user32 = null;
            }
        }
        return user32 != null;
    }

    /** Returns the OS foreground window pointer, or null. */
    public static Object foregroundHandle() {
        if (!available()) {
            return null;
        }
        try {
            return getForegroundWindow.invoke(user32);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Steals OS focus for the given window handle (best-effort). */
    public static void setForeground(Object hwnd) {
        if (!available() || hwnd == null) {
            return;
        }
        try {
            setForegroundWindow.invoke(user32, hwnd);
        } catch (Throwable ignored) {
        }
    }
}
