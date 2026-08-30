package dev.despotes.common.action;

import com.google.gson.JsonObject;
import dev.despotes.common.platform.IGamePlatform;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Semantic function actions (issue 4): F5-class keys as direct, version-tolerant calls
 * instead of relying on the KeyMapping click-queue chain (which is lossy across versions).
 */
public final class FunctionActions {

    private FunctionActions() {
    }

    public static JsonObject run(IGamePlatform p, String fn) {
        Minecraft mc = Minecraft.getInstance();
        boolean handled = true;
        switch (fn) {
            case "toggle-perspective":
                cycleCameraType(mc);
                break;
            case "toggle-debug":
                // The debug overlay is toggled from the key-click queue on the client tick.
                p.injectKey("key.keyboard.f3", true);
                p.injectKey("key.keyboard.f3", false);
                break;
            case "toggle-fullscreen":
                handled = invokeToggleFullscreen(mc);
                break;
            case "toggle-hide-gui":
                // F1: toggle HUD via the vanilla key binding (field name differs by version).
                p.injectKey("key.keyboard.f1", true);
                p.injectKey("key.keyboard.f1", false);
                break;
            case "open-inventory":
                if (mc.player != null) {
                    // Reuse the vanilla key-binding for a reliable open.
                    p.injectKey("key.keyboard.e", true);
                    p.injectKey("key.keyboard.e", false);
                } else {
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
                    handled = false;
                }
                break;
            default:
                handled = false;
                break;
        }
        JsonObject res = new JsonObject();
        res.addProperty("executed", "function");
        res.addProperty("name", fn);
        res.addProperty("handled", handled);
        return res;
    }

    private static void cycleCameraType(Minecraft mc) {
        CameraType cur = mc.options.getCameraType();
        CameraType next;
        if (cur == CameraType.FIRST_PERSON) {
            next = CameraType.THIRD_PERSON_BACK;
        } else if (cur == CameraType.THIRD_PERSON_BACK) {
            next = CameraType.THIRD_PERSON_FRONT;
        } else {
            next = CameraType.FIRST_PERSON;
        }
        mc.options.setCameraType(next);
    }

    private static boolean toggleBoolField(Object owner, String[] names) {
        for (String name : names) {
            try {
                Field f = owner.getClass().getField(name);
                boolean cur = f.getBoolean(owner);
                f.setBoolean(owner, !cur);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static boolean invokeToggleFullscreen(Minecraft mc) {
        try {
            Method m = Minecraft.class.getDeclaredMethod("toggleFullscreen");
            m.setAccessible(true);
            m.invoke(mc);
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
