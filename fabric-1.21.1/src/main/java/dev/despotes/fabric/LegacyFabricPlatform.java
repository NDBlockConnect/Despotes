package dev.despotes.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.despotes.common.action.ScreenshotOptions;
import dev.despotes.common.platform.IGamePlatform;
import dev.despotes.common.platform.PlayerHandle;
import dev.despotes.common.platform.ScreenHandle;
import dev.despotes.common.platform.ShotHandle;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Fabric platform implementation for the legacy obfuscated range
 * (Minecraft 1.20 – 1.21.11, official Mojang mappings). API shapes differ from 26.x:
 * {@code Minecraft.screen} is a public field, GUI listeners take raw doubles,
 * and screenshots read the framebuffer synchronously.
 */
public final class LegacyFabricPlatform implements IGamePlatform {

    @Override
    public String loaderId() {
        return "fabric";
    }

    @Override
    public String mcVersion() {
        return SharedConstants.getCurrentVersion().getId();
    }

    @Override
    public Path gameDir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    @Override
    public void scheduleOnClientThread(Runnable r) {
        Minecraft.getInstance().execute(r);
    }

    @Override
    public <T> T awaitOnClientThread(Supplier<T> task, long timeoutMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            return task.get();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean inGame() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null;
    }

    @Override
    public PlayerHandle player() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? null : new LegacyPlayerHandle(p);
    }

    @Override
    public ScreenHandle screen() {
        Screen s = Minecraft.getInstance().screen;
        return s == null ? null : new LegacyScreenHandle(s);
    }

    @Override
    public int fps() {
        return Minecraft.getInstance().getFps();
    }

    @Override
    public boolean windowFocused() {
        return Minecraft.getInstance().isWindowActive();
    }

    @Override
    public void log(String line) {
        org.slf4j.LoggerFactory.getLogger("Despotes").info(line);
    }

    @Override
    public int keyIdFor(String keyName) {
        InputConstants.Key k = InputConstants.getKey(keyName);
        return k == null ? -1 : k.getValue();
    }

    @Override
    public void injectKey(String keyName, boolean pressed) {
        InputConstants.Key key = InputConstants.getKey(keyName);
        if (key == null) {
            log("[Despotes] unknown key: " + keyName);
            return;
        }
        if (pressed) {
            KeyMapping.set(key, true);
            KeyMapping.click(key);
        } else {
            KeyMapping.set(key, false);
        }
    }

    @Override
    public void setMovement(double forward, double left, boolean jump, boolean sneak, boolean sprint) {
        var o = Minecraft.getInstance().options;
        KeyMapping.set(o.keyUp.key, forward > 0);
        KeyMapping.set(o.keyDown.key, forward < 0);
        KeyMapping.set(o.keyLeft.key, left > 0);
        KeyMapping.set(o.keyRight.key, left < 0);
        KeyMapping.set(o.keyJump.key, jump);
        KeyMapping.set(o.keyShift.key, sneak);
        KeyMapping.set(o.keySprint.key, sprint);
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        p.setYRot(yaw);
        p.setXRot(pitch);
    }

    @Override
    public void openChat() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ChatScreen)) {
            mc.setScreen(new ChatScreen(""));
        }
    }

    @Override
    public void sendChat(String text) {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        if (c != null) {
            c.sendChat(text);
        }
    }

    @Override
    public void injectChars(String text) {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.screen;
        if (s instanceof ChatScreen chat) {
            chat.input.insertText(text);
            return;
        }
        if (s != null) {
            GuiEventListener focused = s.getFocused();
            if (focused instanceof EditBox box) {
                box.insertText(text);
            }
        }
    }

    @Override
    public void injectMouseClick(double x, double y, int button, boolean pressed, boolean shift) {
        Screen s = Minecraft.getInstance().screen;
        if (s == null) {
            return;
        }
        GuiEventListener target = null;
        for (GuiEventListener child : s.children()) {
            if (child.isMouseOver(x, y)) {
                target = child;
                break;
            }
        }
        if (target == null) {
            target = s;
        }
        if (pressed) {
            target.mouseClicked(x, y, button);
        } else {
            target.mouseReleased(x, y, button);
        }
    }

    @Override
    public void worldAttack() {
        Minecraft mc = Minecraft.getInstance();
        Entity target = mc.crosshairPickEntity;
        if (target != null && mc.gameMode != null && mc.player != null) {
            mc.gameMode.attack(mc.player, target);
        }
    }

    @Override
    public void worldUseItem(String hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) {
            return;
        }
        InteractionHand h = "off".equalsIgnoreCase(hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        mc.gameMode.useItem(mc.player, h);
    }

    @Override
    public void worldPlaceBlock(int x, int y, int z, String face, String hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        Direction dir = switch (face.toLowerCase()) {
            case "down" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            case "east" -> Direction.EAST;
            default -> Direction.UP;
        };
        Vec3 location = Vec3.atCenterOf(pos.relative(dir));
        BlockHitResult hit = new BlockHitResult(location, dir, pos, false);
        InteractionHand h = "off".equalsIgnoreCase(hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        mc.gameMode.useItemOn(mc.player, h, hit);
    }

    @Override
    public void worldDropItem(boolean stack) {
        KeyMapping.click(Minecraft.getInstance().options.keyDrop.key);
    }

    @Override
    public void worldPickBlock() {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping.click(mc.options.keyPickItem.key);
    }

    @Override
    public void beginCapture(ScreenshotOptions options, java.util.function.Consumer<ShotHandle> done) {
        Minecraft mc = Minecraft.getInstance();
        try {
            NativeImage img = net.minecraft.client.Screenshot.takeScreenshot(
                    mc.getMainRenderTarget());
            Path tmp = Files.createTempFile("despotes-shot-", ".png");
            img.writeToFile(tmp);
            byte[] bytes = Files.readAllBytes(tmp);
            Files.deleteIfExists(tmp);
            done.accept(new LegacyShotHandle(img.getWidth(), img.getHeight(), "png", bytes));
            img.close();
        } catch (Exception e) {
            log("[Despotes] capture failed: " + e.getMessage());
            done.accept(null);
        }
    }
}
