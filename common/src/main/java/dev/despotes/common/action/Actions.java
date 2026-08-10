package dev.despotes.common.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import dev.despotes.common.platform.IGamePlatform;
import dev.despotes.common.platform.ShotHandle;
import dev.despotes.common.protocol.Json;
import dev.despotes.common.protocol.ProtocolError;
import dev.despotes.common.protocol.Result;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Executes protocol command objects against the platform. All methods run on the client
 * thread unless stated otherwise (screenshot capture hops to the render thread internally).
 */
public final class Actions {

    private static final DateTimeFormatter SHOT_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private Actions() {
    }

    public static Result execute(ActionContext ctx, JsonObject cmd) {
        String type = Json.normalize(Json.getStr(cmd, "type", ""));
        IGamePlatform p = ctx.despotes().platform();
        switch (type) {
            case "key":
                return doKey(ctx, cmd);
            case "type":
                return doType(ctx, cmd);
            case "move":
                return doMove(ctx, cmd);
            case "look":
                return doLook(ctx, cmd);
            case "function":
                return doFunction(ctx, cmd);
            case "click":
                return doClick(ctx, cmd);
            case "use":
                return doUse(ctx, cmd);
            case "mouse":
                return doMouse(ctx, cmd);
            case "screenshot":
                // Intercepted by the dispatcher (async capture path); defensive fallback.
                throw ProtocolError.internal("screenshot must be routed through the async dispatcher path");
            case "status":
                return doStatus(ctx);
            case "screen":
                return doScreenQuery(ctx);
            case "inventory":
                return doInventoryQuery(ctx);
            case "world":
                return Result.ok(ctx.despotes().platform().probeWorld());
            case "blocks":
                return doBlocksQuery(ctx, cmd);
            case "entities":
                return Result.ok(ctx.despotes().platform()
                        .probeEntities(Json.getDouble(cmd, "radius", 8)));
            case "target":
                return Result.ok(ctx.despotes().platform().probeTarget());
            case "container":
                return Result.ok(ctx.despotes().platform().probeContainer());
            case "hotbar":
                return doHotbar(ctx, cmd);
            case "ai":
                return doAi(ctx, cmd);
            case "pending":
                return doPending(ctx);
            case "config-reload":
                ctx.despotes().reloadConfig();
                return Result.ok("config-reload");
            default:
                throw ProtocolError.unknownType(type);
        }
    }

    // ---- key ----

    private static Result doKey(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        JsonArray keys = cmd.has("keys") && cmd.get("keys").isJsonArray()
                ? cmd.getAsJsonArray("keys") : new JsonArray();
        if (keys.isEmpty()) {
            throw ProtocolError.badRequest("'keys' is required");
        }
        String op = Json.normalize(Json.getStr(cmd, "op", "tap"));
        int holdTicks = Math.max(1, Json.getInt(cmd, "holdTicks", 1));
        int repeat = Math.max(1, Json.getInt(cmd, "repeat", 1));
        int interval = Math.max(0, Json.getInt(cmd, "intervalTicks", 0));

        boolean press = op.equals("press") || op.equals("tap");
        boolean release = op.equals("release") || op.equals("tap");

        for (int r = 0; r < repeat; r++) {
            for (var k : keys) {
                String keyName = k.getAsString();
                if (press) {
                    p.injectKey(keyName, true);
                }
                if (op.equals("tap")) {
                    ctx.despotes().dispatcher().scheduleInTicks(holdTicks,
                            () -> p.injectKey(keyName, false));
                } else if (release && !press) {
                    p.injectKey(keyName, false);
                }
            }
            if (repeat > 1 && interval > 0) {
                // Spacing between repeats is handled by delaying subsequent rounds.
                final int round = r + 1;
                if (round < repeat) {
                    ctx.despotes().dispatcher().scheduleInTicks(interval, () -> {
                    });
                }
            }
        }
        JsonObject res = new JsonObject();
        res.addProperty("executed", "key");
        res.addProperty("op", op);
        res.addProperty("keys", keys.size());
        res.addProperty("holdTicks", holdTicks);
        return Result.ok(res);
    }

    // ---- type ----

    private static Result doType(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        String text = Json.getStr(cmd, "text", "");
        if (text.isEmpty()) {
            throw ProtocolError.badRequest("'text' is required");
        }
        String target = Json.normalize(Json.getStr(cmd, "target", "chat"));
        boolean submit = Json.getBool(cmd, "submit", true);

        switch (target) {
            case "chat": {
                if (text.startsWith("/")) {
                    p.sendChat(text);
                } else {
                    p.openChat();
                    p.injectChars(text);
                    if (submit) {
                        p.injectKey("key.keyboard.enter", true);
                        ctx.despotes().dispatcher().scheduleInTicks(2,
                                () -> p.injectKey("key.keyboard.enter", false));
                    }
                }
                break;
            }
            case "command":
                p.sendChat(text.startsWith("/") ? text : "/" + text);
                break;
            case "focused":
                ctx.requireScreen();
                p.injectChars(text);
                if (submit) {
                    p.injectKey("key.keyboard.enter", true);
                    ctx.despotes().dispatcher().scheduleInTicks(2,
                            () -> p.injectKey("key.keyboard.enter", false));
                }
                break;
            default:
                throw ProtocolError.badRequest("unknown 'target': " + target);
        }
        JsonObject res = new JsonObject();
        res.addProperty("executed", "type");
        res.addProperty("target", target);
        res.addProperty("chars", text.length());
        return Result.ok(res);
    }

    // ---- move ----

    private static Result doMove(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        double forward = clampAxis(Json.getDouble(cmd, "forward", 0));
        double left = clampAxis(Json.getDouble(cmd, "left", 0));
        boolean jump = Json.getBool(cmd, "jump", false);
        boolean sneak = Json.getBool(cmd, "sneak", false);
        boolean sprint = Json.getBool(cmd, "sprint", false);
        int duration = Math.max(0, Json.getInt(cmd, "durationTicks", 1));

        p.setMovement(forward, left, jump, sneak, sprint);
        if (duration > 0) {
            ctx.despotes().dispatcher().scheduleInTicks(duration,
                    () -> p.setMovement(0, 0, false, false, false));
        }
        JsonObject res = new JsonObject();
        res.addProperty("executed", "move");
        res.addProperty("durationTicks", duration);
        return Result.ok(res);
    }

    private static double clampAxis(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }

    // ---- look ----

    private static Result doLook(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        String mode = Json.normalize(Json.getStr(cmd, "mode", "delta"));
        float yaw = (float) Json.getDouble(cmd, "yaw", 0);
        float pitch = clampPitch((float) Json.getDouble(cmd, "pitch", 0));
        int smooth = Math.max(1, Json.getInt(cmd, "smoothTicks",
                ctx.despotes().config().movement.defaultLookSmoothTicks));

        var player = p.player();
        if (player == null) {
            throw ProtocolError.notInGame();
        }
        float targetYaw;
        float targetPitch;
        if (mode.equals("absolute")) {
            targetYaw = yaw;
            targetPitch = pitch;
        } else if (mode.equals("delta")) {
            targetYaw = player.yaw() + yaw;
            targetPitch = clampPitch(player.pitch() + pitch);
        } else {
            throw ProtocolError.badRequest("unknown 'mode': " + mode);
        }

        // Frame-driven easing (issue 2) unless disabled. smoothTicks<=1 => instant.
        long durationMs = smooth <= 1 ? 0
                : (long) Json.getDouble(cmd, "durationMs",
                        ctx.despotes().config().movement.lookSmoothMs);
        ctx.despotes().lookSmoother().start(targetYaw, targetPitch, durationMs);
        JsonObject res = new JsonObject();
        res.addProperty("executed", "look");
        res.addProperty("mode", mode);
        res.addProperty("yaw", targetYaw);
        res.addProperty("pitch", targetPitch);
        return Result.ok(res);
    }

    private static float clampPitch(float pitch) {
        return Math.max(-89.9f, Math.min(89.9f, pitch));
    }

    // ---- function (semantic keys, issue 4) ----

    // ---- AI intent translation (Alpha.7) ----

    private static final java.util.Set<String> AI_ALLOWED = java.util.Set.of(
            "key", "move", "look", "click", "use", "function", "hotbar");

    private static Result doAi(ActionContext ctx, JsonObject cmd) {
        var cfg = ctx.despotes().config().ai;
        if (!cfg.enabled) {
            throw ProtocolError.forbidden("ai is disabled (set ai.enabled=true)");
        }
        String intent = Json.getStr(cmd, "intent", "");
        if (intent.isBlank()) {
            throw ProtocolError.badRequest("'intent' is required");
        }
        String world = ctx.despotes().platform().awaitOnClientThread(() -> {
            var p = ctx.despotes().platform();
            JsonObject o = p.probeWorld();
            if (p.player() != null) {
                o.add("player", p.player().statusJson());
            }
            return o.toString();
        }, 3000);
        String system = "You control a Minecraft player via JSON action commands. "
                + "Reply ONLY with a JSON array of action objects using these types: "
                + "key/move/look/click/use/function/hotbar. No prose, no markdown.";
        String user = "World state: " + world + " Intent: " + intent;
        String plan;
        try {
            plan = dev.despotes.common.ai.AiClient.chat(cfg, system, user);
        } catch (Exception e) {
            throw ProtocolError.internal("AI endpoint failed: " + e.getMessage());
        }
        com.google.gson.JsonElement parsed;
        try {
            parsed = com.google.gson.JsonParser.parseString(plan.trim());
        } catch (Exception e) {
            throw ProtocolError.internal("AI returned non-JSON: " + plan);
        }
        if (!parsed.isJsonArray()) {
            throw ProtocolError.internal("AI must return a JSON array of actions");
        }
        int max = Math.max(1, cfg.maxActions);
        com.google.gson.JsonArray results = new com.google.gson.JsonArray();
        int n = 0;
        for (var el : parsed.getAsJsonArray()) {
            if (n++ >= max) break;
            if (!el.isJsonObject()) continue;
            JsonObject a = el.getAsJsonObject();
            String type = Json.normalize(Json.getStr(a, "type", ""));
            if (!AI_ALLOWED.contains(type)) continue;
            Result r = execute(ctx, a);
            results.add(com.google.gson.JsonParser.parseString(r.toJsonString(ctx.requestId())));
        }
        JsonObject res = new JsonObject();
        res.addProperty("executed", "ai");
        res.add("results", results);
        return Result.ok(res);
    }

    private static Result doHotbar(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        int slot = Json.getInt(cmd, "slot", 0);
        if (slot < 0 || slot > 8) {
            throw ProtocolError.badRequest("slot must be 0-8");
        }
        p.selectHotbarSlot(slot);
        JsonObject res = new JsonObject();
        res.addProperty("executed", "hotbar");
        res.addProperty("slot", slot);
        return Result.ok(res);
    }

    private static Result doFunction(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        String fn = Json.normalize(Json.getStr(cmd, "name", ""));
        if (fn.isEmpty()) {
            throw ProtocolError.badRequest("'name' is required");
        }
        boolean handled = p.runFunction(fn);
        JsonObject res = new JsonObject();
        res.addProperty("executed", "function");
        res.addProperty("name", fn);
        res.addProperty("handled", handled);
        if (!handled) {
            res.addProperty("note", "platform does not support this function");
        }
        return Result.ok(res);
    }

    private static Result doBlocksQuery(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        var player = p.player();
        int x = (int) Math.floor(player.x());
        int y = (int) Math.floor(player.y());
        int z = (int) Math.floor(player.z());
        if (cmd.has("x")) x = Json.getInt(cmd, "x", x);
        if (cmd.has("y")) y = Json.getInt(cmd, "y", y);
        if (cmd.has("z")) z = Json.getInt(cmd, "z", z);
        int r = Math.min(8, Math.max(1, Json.getInt(cmd, "radius", 3)));
        return Result.ok(p.probeBlocks(x, y, z, r));
    }

    // ---- click ----

    private static Result doClick(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        if (p.screen() == null || !p.screen().open()) {
            // No GUI open: fall back to world interaction via crosshair (requires a world).
            ctx.requireInGame();
            int button = Json.getInt(cmd, "button", 0);
            if (button == 0) {
                p.worldAttack();
            } else {
                p.worldUseItem("main");
            }
            JsonObject res = new JsonObject();
            res.addProperty("executed", "click");
            res.addProperty("fallback", "world");
            return Result.ok(res);
        }
        double x = Json.getDouble(cmd, "x", 0);
        double y = Json.getDouble(cmd, "y", 0);
        int button = Json.getInt(cmd, "button", 0);
        boolean shift = Json.getBool(cmd, "shift", false);
        String op = Json.normalize(Json.getStr(cmd, "op", "click"));

        switch (op) {
            case "press":
                p.injectMouseClick(x, y, button, true, shift);
                break;
            case "release":
                p.injectMouseClick(x, y, button, false, shift);
                break;
            case "click":
            case "double":
                p.injectMouseClick(x, y, button, true, shift);
                ctx.despotes().dispatcher().scheduleInTicks(1,
                        () -> p.injectMouseClick(x, y, button, false, shift));
                if (op.equals("double")) {
                    ctx.despotes().dispatcher().scheduleInTicks(3,
                            () -> p.injectMouseClick(x, y, button, true, shift));
                    ctx.despotes().dispatcher().scheduleInTicks(4,
                            () -> p.injectMouseClick(x, y, button, false, shift));
                }
                break;
            default:
                throw ProtocolError.badRequest("unknown click 'op': " + op);
        }
        JsonObject res = new JsonObject();
        res.addProperty("executed", "click");
        res.addProperty("x", x);
        res.addProperty("y", y);
        res.addProperty("button", button);
        res.addProperty("op", op);
        return Result.ok(res);
    }

    // ---- use (world interaction) ----

    private static Result doUse(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        String what = Json.normalize(Json.getStr(cmd, "what", "attack"));
        String hand = Json.normalize(Json.getStr(cmd, "hand", "main"));
        switch (what) {
            case "attack":
                p.worldAttack();
                break;
            case "useitem":
                p.worldUseItem(hand);
                break;
            case "placeblock": {
                JsonObject t = Json.getObj(cmd, "target");
                if (t == null) {
                    throw ProtocolError.badRequest("placeBlock requires 'target'");
                }
                int x = Json.getInt(t, "x", 0);
                int y = Json.getInt(t, "y", 0);
                int z = Json.getInt(t, "z", 0);
                String face = Json.normalize(Json.getStr(t, "face", "up"));
                p.worldPlaceBlock(x, y, z, face, hand);
                break;
            }
            case "drop":
                p.worldDropItem(Json.getBool(cmd, "stack", false));
                break;
            case "pickblock":
                p.worldPickBlock();
                break;
            default:
                throw ProtocolError.badRequest("unknown 'what': " + what);
        }
        JsonObject res = new JsonObject();
        res.addProperty("executed", "use");
        res.addProperty("what", what);
        return Result.ok(res);
    }

    // ---- mouse capture ----

    /**
     * Explicitly grab or release the mouse cursor:
     * {"type":"mouse","op":"grab"|"release"|"status"}.
     */
    private static Result doMouse(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        String op = Json.normalize(Json.getStr(cmd, "op", "status"));
        switch (op) {
            case "grab":
                p.grabMouseCapture();
                break;
            case "release":
                p.releaseMouseCapture();
                break;
            case "status":
                break;
            default:
                throw ProtocolError.badRequest("unknown mouse 'op': " + op);
        }
        JsonObject res = new JsonObject();
        res.addProperty("executed", "mouse");
        res.addProperty("op", op);
        res.addProperty("captured", p.isMouseCaptured());
        return Result.ok(res);
    }

    // ---- screenshot ----

    /**
     * Async screenshot execution. Invokes {@code done} with the final result on the
     * capture callback thread (never blocks the client thread).
     */
    public static void executeScreenshotAsync(ActionContext ctx, JsonObject cmd,
                                              java.util.function.Consumer<Result> done) {
        IGamePlatform p = ctx.despotes().platform();
        ScreenshotOptions opts = ScreenshotOptions.fromJson(cmd);
        long deadline = System.currentTimeMillis()
                + ctx.despotes().config().http.screenshotTimeoutMs;
        java.util.concurrent.atomic.AtomicBoolean settled = new java.util.concurrent.atomic.AtomicBoolean(false);

        p.beginCapture(opts, shot -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            if (shot == null) {
                done.accept(Result.fail(ProtocolError.timeout("screenshot capture failed")));
                return;
            }
            try {
                done.accept(buildScreenshotResult(ctx, shot, opts));
            } catch (Throwable t) {
                done.accept(Result.fail(ProtocolError.internal(String.valueOf(t.getMessage()))));
            }
        });

        // Watchdog: fail the request if the capture callback never fires.
        Thread watchdog = new Thread(() -> {
            long remaining = deadline - System.currentTimeMillis();
            try {
                Thread.sleep(Math.max(50, remaining));
            } catch (InterruptedException ignored) {
            }
            if (settled.compareAndSet(false, true)) {
                done.accept(Result.fail(ProtocolError.timeout("screenshot capture timed out")));
            }
        }, "Despotes-shot-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static Result buildScreenshotResult(ActionContext ctx, ShotHandle shot,
                                                ScreenshotOptions opts) throws java.io.IOException {
        IGamePlatform p = ctx.despotes().platform();
        JsonObject res = new JsonObject();
        res.addProperty("executed", "screenshot");
        res.addProperty("width", shot.width());
        res.addProperty("height", shot.height());
        res.addProperty("format", shot.format());
        if (opts.save) {
            java.nio.file.Path dir = opts.path != null
                    ? java.nio.file.Path.of(opts.path)
                    : p.gameDir().resolve(ctx.despotes().config().capture.dir);
            java.nio.file.Files.createDirectories(dir);
            String name = LocalDateTime.now().format(SHOT_STAMP) + "-"
                    + safeId(ctx.requestId()) + "." + shot.format();
            java.nio.file.Path file = dir.resolve(name);
            java.nio.file.Files.write(file, shot.encoded());
            res.addProperty("path", file.toAbsolutePath().toString());
        } else {
            res.addProperty("base64", Base64.getEncoder().encodeToString(shot.encoded()));
        }
        return Result.ok(res);
    }

    private static String safeId(String s) {
        if (s == null || s.isBlank()) {
            return "req";
        }
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                b.append(c);
            }
            if (b.length() >= 24) {
                break;
            }
        }
        return b.length() == 0 ? "req" : b.toString();
    }

    // ---- inventory query ----

    private static Result doInventoryQuery(ActionContext ctx) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        var player = p.player();
        if (player == null) {
            throw ProtocolError.notInGame();
        }
        return Result.ok(player.inventoryJson());
    }

    // ---- queries ----

    private static Result doStatus(ActionContext ctx) {
        return Result.ok(ctx.despotes().statusJson());
    }

    private static Result doScreenQuery(ActionContext ctx) {
        IGamePlatform p = ctx.despotes().platform();
        JsonObject res = new JsonObject();
        var screen = p.screen();
        if (screen == null || !screen.open()) {
            res.addProperty("open", false);
            return Result.ok(res);
        }
        res.addProperty("open", true);
        res.addProperty("title", screen.title());
        res.addProperty("width", screen.width());
        res.addProperty("height", screen.height());
        res.add("widgets", screen.widgetTree(4));
        return Result.ok(res);
    }

    private static Result doPending(ActionContext ctx) {
        JsonObject res = new JsonObject();
        res.addProperty("queueSize", ctx.despotes().dispatcher().queueSize());
        res.add("executing", ctx.despotes().dispatcher().executingJson());
        return Result.ok(res);
    }
}
