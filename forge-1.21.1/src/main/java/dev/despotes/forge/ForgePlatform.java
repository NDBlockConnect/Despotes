package dev.despotes.forge;

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
 * Forge platform implementation for the legacy official-mapping range
 * (Minecraft 1.21.1). Mirrors the Fabric legacy platform API shapes.
 */
public final class ForgePlatform implements IGamePlatform {

    @Override
    public String loaderId() {
        return "neoforge";
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
        return p == null ? null : new ForgePlayerHandle(p);
    }

    @Override
    public ScreenHandle screen() {
        Screen s = Minecraft.getInstance().screen;
        return s == null ? null : new ForgeScreenHandle(s);
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

    // ---- key injection ----

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
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen != null && key.getType() != InputConstants.Type.MOUSE) {
            // Route through the open screen so keys like ESC close menus.
            if (pressed) {
                screen.keyPressed(key.getValue(), key.getValue(), 0);
            } else {
                screen.keyReleased(key.getValue(), key.getValue(), 0);
            }
            return;
        }
        if (pressed) {
            KeyMapping.set(key, true);
            KeyMapping.click(key);
        } else {
            KeyMapping.set(key, false);
        }
    }

    // ---- movement ----

    @Override
    public void setMovement(double forward, double left, boolean jump, boolean sneak, boolean sprint) {
        Minecraft mc = Minecraft.getInstance();
        var o = mc.options;
        KeyMapping.set(MinecraftKeyAccess.boundKey(o.keyUp), forward > 0);
        KeyMapping.set(MinecraftKeyAccess.boundKey(o.keyDown), forward < 0);
        KeyMapping.set(MinecraftKeyAccess.boundKey(o.keyLeft), left > 0);
        KeyMapping.set(MinecraftKeyAccess.boundKey(o.keyRight), left < 0);
        KeyMapping.set(MinecraftKeyAccess.boundKey(o.keyJump), jump);
        KeyMapping.set(MinecraftKeyAccess.boundKey(o.keyShift), sneak);
        KeyMapping.set(MinecraftKeyAccess.boundKey(o.keySprint), sprint);
    }

    // ---- view rotation ----

    @Override
    public void setRotation(float yaw, float pitch) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        p.setYRot(yaw);
        p.setXRot(pitch);
    }

    // ---- chat / typing ----

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
            EditBox box = MinecraftKeyAccess.chatInput(chat);
            if (box != null) {
                box.insertText(text);
            }
            return;
        }
        if (s != null) {
            GuiEventListener focused = s.getFocused();
            if (focused instanceof EditBox box) {
                box.insertText(text);
            }
        }
    }

    // ---- mouse clicks on GUI ----

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

    // ---- world interactions ----

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
        KeyMapping.click(MinecraftKeyAccess.boundKey(Minecraft.getInstance().options.keyDrop));
    }

    @Override
    public void worldPickBlock() {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping.click(MinecraftKeyAccess.boundKey(mc.options.keyPickItem));
    }

    // ---- screenshot ----

    @Override
    public void beginCapture(ScreenshotOptions options, java.util.function.Consumer<ShotHandle> done) {
        Minecraft mc = Minecraft.getInstance();
        try {
            NativeImage img = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget());
            Path tmp = Files.createTempFile("despotes-shot-", ".png");
            img.writeToFile(tmp);
            byte[] bytes = Files.readAllBytes(tmp);
            Files.deleteIfExists(tmp);
            done.accept(new ForgeShotHandle(img.getWidth(), img.getHeight(), "png", bytes));
            img.close();
        } catch (Exception e) {
            log("[Despotes] capture failed: " + e.getMessage());
            done.accept(null);
        }
    }

    // ---- mouse capture (focus-safe) ----

    @Override
    public void releaseMouseCapture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
    }

    @Override
    public void grabMouseCapture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.level == null) {
            return;
        }
        mc.mouseHandler.grabMouse();
    }

    @Override
    public boolean isMouseCaptured() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.mouseHandler.isMouseGrabbed();
    }

    @Override
    public boolean runFunction(String fn) {
        return dev.despotes.common.action.FunctionActions.run(this, fn).get("handled").getAsBoolean();
    }

    @Override
    public com.google.gson.JsonObject probeWorld() {
        return dev.despotes.common.probe.WorldProbes.world(Minecraft.getInstance());
    }

    @Override
    public com.google.gson.JsonObject probeBlocks(int x, int y, int z, int r) {
        return dev.despotes.common.probe.WorldProbes.blocks(Minecraft.getInstance(), x, y, z, r);
    }

    @Override
    public com.google.gson.JsonObject probeEntities(double radius) {
        return dev.despotes.common.probe.WorldProbes.entities(Minecraft.getInstance(), radius);
    }

    @Override
    public com.google.gson.JsonObject probeTarget() {
        return dev.despotes.common.probe.WorldProbes.target(Minecraft.getInstance());
    }

    @Override
    public void setWindowMinimized(boolean minimized) {
        dev.despotes.common.focus.WindowControl.setMinimized(Minecraft.getInstance().getWindow(), minimized);
    }

    @Override
    public void setPauseOnLostFocus(boolean enabled) {
        Minecraft.getInstance().options.pauseOnLostFocus = enabled;
    }
}
