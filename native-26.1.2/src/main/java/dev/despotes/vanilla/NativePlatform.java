package dev.despotes.vanilla;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.despotes.common.Despotes;
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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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

/** Fabric (1.21.9 / protocol 26.2) implementation of the platform SPI. */
public final class NativePlatform implements IGamePlatform {

    @Override
    public String loaderId() {
        return "native";
    }

    @Override
    public String mcVersion() {
        return SharedConstants.getCurrentVersion().id();
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
        return p == null ? null : new NativePlayerHandle(p);
    }

    @Override
    public ScreenHandle screen() {
        Screen s = Minecraft.getInstance().screen;
        return s == null ? null : new NativeScreenHandle(s);
    }

    @Override
    public int fps() {
        return Minecraft.getInstance().getFps();
    }

    @Override
    public boolean windowFocused() {
        return Minecraft.getInstance().getWindow().isFocused();
    }

    @Override
    public void log(String line) {
        System.out.println("[Despotes] " + line);
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
            net.minecraft.client.input.KeyEvent event =
                    new net.minecraft.client.input.KeyEvent(key.getValue(), key.getValue(), 0);
            if (pressed) {
                screen.keyPressed(event);
            } else {
                screen.keyReleased(event);
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
            mc.setScreen(new ChatScreen("", false));
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
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(button, shift ? 1 : 0));
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
            target.mouseClicked(event, shift);
        } else {
            target.mouseReleased(event);
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
        Minecraft mc = Minecraft.getInstance();
        var o = mc.options;
        KeyMapping.click(MinecraftKeyAccess.boundKey(o.keyDrop));
    }

    @Override
    public void worldPickBlock() {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && mc.gameMode != null) {
            mc.gameMode.handlePickItemFromBlock(blockHit.getBlockPos(), false);
        } else if (hit instanceof EntityHitResult entityHit && mc.gameMode != null) {
            mc.gameMode.handlePickItemFromEntity(entityHit.getEntity(), false);
        }
    }

    // ---- screenshot (synchronous framebuffer readback) ----

    @Override
    public void beginCapture(ScreenshotOptions options, java.util.function.Consumer<ShotHandle> done) {
        Minecraft mc = Minecraft.getInstance();
        try {
            java.util.concurrent.atomic.AtomicBoolean settled =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget(), img -> {
                if (!settled.compareAndSet(false, true)) {
                    return;
                }
                try {
                    Path tmp = Files.createTempFile("despotes-shot-", ".png");
                    img.writeToFile(tmp);
                    byte[] bytes = Files.readAllBytes(tmp);
                    Files.deleteIfExists(tmp);
                    done.accept(new NativeShotHandle(img.getWidth(), img.getHeight(), "png", bytes));
                    img.close();
                } catch (Exception e) {
                    log("[Despotes] capture encode failed: " + e.getMessage());
                    done.accept(null);
                }
            });
        } catch (Exception e) {
            log("[Despotes] capture failed: " + e.getMessage());
            done.accept(null);
        }
    }

    @Override
    public void drawOverlay(java.util.List<String> lines) {
        // Rendering is performed by HudOverlayMixin directly; kept as no-op hook.
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
