package dev.despotes.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

import java.lang.reflect.Field;

/**
 * Reflective accessors for Minecraft members whose visibility or availability differs
 * across loaders/versions. Reflection keeps the legacy platform source independent of
 * access wideners (which Loom rewrites to intermediary and which the official-mapping
 * runtime would reject).
 */
public final class MinecraftKeyAccess {

    private static Field keyField;
    private static Field chatInputField;

    private MinecraftKeyAccess() {
    }

    public static InputConstants.Key boundKey(KeyMapping mapping) {
        try {
            if (keyField == null) {
                keyField = KeyMapping.class.getDeclaredField("key");
                keyField.setAccessible(true);
            }
            return (InputConstants.Key) keyField.get(mapping);
        } catch (Exception e) {
            return mapping.getDefaultKey();
        }
    }

    public static EditBox chatInput(ChatScreen screen) {
        try {
            if (chatInputField == null) {
                chatInputField = ChatScreen.class.getDeclaredField("input");
                chatInputField.setAccessible(true);
            }
            return (EditBox) chatInputField.get(screen);
        } catch (Exception e) {
            return null;
        }
    }
}
