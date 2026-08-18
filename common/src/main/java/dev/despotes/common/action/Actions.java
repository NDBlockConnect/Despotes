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
            case "chat":
                return doChat(ctx, cmd);
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
            case "self":
                return Result.ok(ctx.despotes().platform().probeSelf());
            case "threats":
                return Result.ok(ctx.despotes().platform()
                        .probeThreats(Json.getDouble(cmd, "radius", 12)));
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
            case "recipe":
            case "recipes":
                return Result.ok(ctx.despotes().platform().probeRecipes());
            case "players":
                return Result.ok(ctx.despotes().platform()
                        .probePlayers(Json.getDouble(cmd, "radius", 64)));
            case "server":
                return Result.ok(ctx.despotes().platform().probeServer());
            case "tablist":
                return Result.ok(ctx.despotes().platform().probeTablist());
            case "scoreboard":
                return Result.ok(ctx.despotes().platform().probeScoreboard());
            case "coords":
                return Result.ok(ctx.despotes().platform().probeCoords());
            case "whisper":
                return doWhisper(ctx, cmd);
            case "goto":
                return doGoto(ctx, cmd);
            case "follow":
                return doFollow(ctx, cmd);
            case "stop-nav":
            case "stopnav":
                ctx.despotes().navigator().stop();
                return Result.ok("stopped");
            case "inventory-action":
                return doInventoryAction(ctx, cmd);
            case "craft":
                return doCraft(ctx, cmd);
            case "interact":
                return doInteract(ctx, cmd);
            case "trade":
                return doTrade(ctx, cmd);
            case "sort":
                return doSort(ctx, cmd);
            case "equip":
                return doEquip(ctx, cmd);
            case "hotbar":
                return doHotbar(ctx, cmd);
            case "respawn":
                return doRespawn(ctx);
            case "ping":
                return doPing(ctx);
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
                    p.sendCommand(text);
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
                p.sendCommand(text);
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

    // ---- chat ----

    /**
     * Alpha.9: direct chat helper. Commands (text starting with '/') go straight through
     * sendChat; plain text opens the chat screen, injects characters and submits.
     * Simpler than "type" for the common conversational case.
     */
    private static Result doChat(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        String text = Json.getStr(cmd, "text", "");
        if (text.isEmpty()) {
            throw ProtocolError.badRequest("'text' is required");
        }
        boolean submit = Json.getBool(cmd, "submit", true);
        String via;
        if (text.startsWith("/")) {
            p.sendCommand(text);
            via = "command";
            submit = true;
        } else {
            p.openChat();
            p.injectChars(text);
            if (submit) {
                p.injectKey("key.keyboard.enter", true);
                ctx.despotes().dispatcher().scheduleInTicks(2,
                        () -> p.injectKey("key.keyboard.enter", false));
            }
            via = "chat_screen";
        }
        JsonObject res = new JsonObject();
        res.addProperty("executed", "chat");
        res.addProperty("via", via);
        res.addProperty("chars", text.length());
        res.addProperty("submitted", submit);
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
        } else if (mode.equals("lookat")) {
            // v26.2-Alpha.7: face a coordinate or an entity (by uuid).
            double tx;
            double ty;
            double tz;
            String uuid = Json.getStr(cmd, "uuid", "");
            if (!uuid.isBlank()) {
                JsonObject found = p.findEntity(uuid);
                if (!Json.getBool(found, "found", false)) {
                    throw ProtocolError.badRequest("entity not found or not loaded: " + uuid);
                }
                tx = Json.getDouble(found, "x", 0);
                ty = Json.getDouble(found, "y", 0);
                tz = Json.getDouble(found, "z", 0);
            } else if (cmd.has("x") && cmd.has("y") && cmd.has("z")) {
                tx = Json.getDouble(cmd, "x", 0);
                ty = Json.getDouble(cmd, "y", 0);
                tz = Json.getDouble(cmd, "z", 0);
            } else {
                throw ProtocolError.badRequest("lookat requires 'x'/'y'/'z' or 'uuid'");
            }
            double eyeY = player.y() + 1.62;
            double dx = tx - player.x();
            double dy = ty - eyeY;
            double dz = tz - player.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            targetPitch = clampPitch((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
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

    // ---- ping (v26.2-Alpha.7 health check) ----

    /**
     * Cheap liveness probe that works from any screen: returns the dispatcher heartbeat
     * (client ticks since boot), queue size, fps and a wall-clock stamp. Round-tripping this
     * tells a caller both that the transport is alive and that the game loop is ticking.
     */
    private static Result doPing(ActionContext ctx) {
        JsonObject res = new JsonObject();
        res.addProperty("executed", "pong");
        res.addProperty("tickCount", ctx.despotes().dispatcher().tickCount());
        res.addProperty("queueSize", ctx.despotes().dispatcher().queueSize());
        res.addProperty("inGame", ctx.despotes().platform().inGame());
        res.addProperty("fps", ctx.despotes().platform().fps());
        res.addProperty("timestampMs", System.currentTimeMillis());
        return Result.ok(res);
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

    // ---- inventory-action (v26.3-Alpha.1) ----

    /**
     * Inventory slot manipulation via the open container menu. Supports:
     * <ul>
     *   <li><b>moveSlot</b>: move item from slot A to slot B (pickup A, place at B)</li>
     *   <li><b>quickMove</b>: shift-click a slot (auto-move to other inventory section)</li>
     *   <li><b>drop</b>: click outside inventory to drop (THROW click)</li>
     *   <li><b>split</b>: right-click to split a stack in half</li>
     *   <li><b>swap</b>: swap with hotbar slot (SWAP click type, button = hotbar 0-8)</li>
     * </ul>
     *
     * Requires a container menu to be open (player.containerMenu must not be the default
     * player inventory, or a screen must be open with the menu active).
     */
    private static Result doInventoryAction(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        String op = Json.normalize(Json.getStr(cmd, "op", "quickMove"));

        switch (op) {
            case "moveslot": {
                int fromSlot = Json.getInt(cmd, "fromSlot", -1);
                int toSlot = Json.getInt(cmd, "toSlot", -1);
                if (fromSlot < 0 || toSlot < 0) {
                    throw ProtocolError.badRequest("moveSlot requires 'fromSlot' and 'toSlot'");
                }
                // Pickup from slot, then place at target slot
                boolean ok1 = p.slotClick(fromSlot, 0, "pickup", 0);
                boolean ok2 = p.slotClick(toSlot, 0, "pickup", 0);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "inventory-action");
                res.addProperty("op", "moveSlot");
                res.addProperty("fromSlot", fromSlot);
                res.addProperty("toSlot", toSlot);
                res.addProperty("dispatched", ok1 && ok2);
                return Result.ok(res);
            }
            case "quickmove": {
                int slot = Json.getInt(cmd, "slot", -1);
                if (slot < 0) {
                    throw ProtocolError.badRequest("quickMove requires 'slot'");
                }
                boolean ok = p.slotClick(slot, 0, "quick_move", 0);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "inventory-action");
                res.addProperty("op", "quickMove");
                res.addProperty("slot", slot);
                res.addProperty("dispatched", ok);
                return Result.ok(res);
            }
            case "drop": {
                int slot = Json.getInt(cmd, "slot", -1);
                if (slot < 0) {
                    throw ProtocolError.badRequest("drop requires 'slot'");
                }
                // THROW click with button=0 drops one item; button=1 drops entire stack
                int button = Json.getBool(cmd, "stack", false) ? 1 : 0;
                boolean ok = p.slotClick(slot, button, "throw", 0);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "inventory-action");
                res.addProperty("op", "drop");
                res.addProperty("slot", slot);
                res.addProperty("stack", Json.getBool(cmd, "stack", false));
                res.addProperty("dispatched", ok);
                return Result.ok(res);
            }
            case "split": {
                int slot = Json.getInt(cmd, "slot", -1);
                if (slot < 0) {
                    throw ProtocolError.badRequest("split requires 'slot'");
                }
                // Right-click picks up half the stack
                boolean ok = p.slotClick(slot, 1, "pickup", 0);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "inventory-action");
                res.addProperty("op", "split");
                res.addProperty("slot", slot);
                res.addProperty("dispatched", ok);
                return Result.ok(res);
            }
            case "swap": {
                int slot = Json.getInt(cmd, "slot", -1);
                int hotbarSlot = Json.getInt(cmd, "hotbarSlot", -1);
                if (slot < 0 || hotbarSlot < 0 || hotbarSlot > 8) {
                    throw ProtocolError.badRequest("swap requires 'slot' (0+) and 'hotbarSlot' (0-8)");
                }
                // SWAP click type: button = hotbar slot index
                boolean ok = p.slotClick(slot, hotbarSlot, "swap", 0);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "inventory-action");
                res.addProperty("op", "swap");
                res.addProperty("slot", slot);
                res.addProperty("hotbarSlot", hotbarSlot);
                res.addProperty("dispatched", ok);
                return Result.ok(res);
            }
            case "pickup": {
                int slot = Json.getInt(cmd, "slot", -1);
                if (slot < 0) {
                    throw ProtocolError.badRequest("pickup requires 'slot'");
                }
                int button = Json.getInt(cmd, "button", 0);
                boolean ok = p.slotClick(slot, button, "pickup", 0);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "inventory-action");
                res.addProperty("op", "pickup");
                res.addProperty("slot", slot);
                res.addProperty("button", button);
                res.addProperty("dispatched", ok);
                return Result.ok(res);
            }
            default:
                throw ProtocolError.badRequest("unknown inventory-action 'op': " + op);
        }
    }

    // ---- craft (v26.3-Alpha.3) ----

    /**
     * Craft items by placing materials into the crafting grid and extracting the result.
     *
     * <p>Two modes:
     * <ul>
     *   <li><b>recipe</b>: pass a recipe pattern with ingredient slot→sourceSlot mappings;
     *       the action places items into the grid, then clicks the result slot.</li>
     *   <li><b>result</b>: just click the result slot (slot 0) — useful when the grid is
     *       already filled (e.g. the recipe book auto-filled it).</li>
     * </ul>
     *
     * Requires a crafting menu to be open (player inventory 2x2 or crafting table 3x3).
     * In the InventoryMenu, the crafting grid slots are 1-4 (2x2) or 1-9 (3x3) and the
     * result slot is 0.
     *
     * Example (craft a crafting table from 4 planks in 2x2):
     * <pre>{@code
     * {"type":"craft","mode":"recipe","grid":{"1":37,"2":37,"3":37,"4":37}}
     * }</pre>
     *
     * Example (just take the result):
     * <pre>{@code
     * {"type":"craft","mode":"result"}
     * }</pre>
     */
    private static Result doCraft(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        String mode = Json.normalize(Json.getStr(cmd, "mode", "result"));

        switch (mode) {
            case "recipe": {
                JsonObject grid = Json.getObj(cmd, "grid");
                if (grid == null || grid.size() == 0) {
                    throw ProtocolError.badRequest("craft recipe mode requires 'grid' (slot→sourceSlot map)");
                }
                // Place items: for each grid slot, right-click source (picks up 1 item),
                // then left-click grid slot (places the 1 item)
                int count = 0;
                for (String gridSlotStr : grid.keySet()) {
                    int gridSlot;
                    try {
                        gridSlot = Integer.parseInt(gridSlotStr);
                    } catch (NumberFormatException e) {
                        throw ProtocolError.badRequest("grid slot keys must be integers (1-9), got: " + gridSlotStr);
                    }
                    int sourceSlot = Json.getInt(grid, gridSlotStr, -1);
                    if (sourceSlot < 0) continue;
                    // Right-click source: picks up 1 item from the stack
                    p.slotClick(sourceSlot, 1, "pickup", 0);
                    // Left-click grid slot: places the 1 item
                    p.slotClick(gridSlot, 0, "pickup", 0);
                    count++;
                }
                // Extract result: use quick_move (shift-click) on result slot 0 to auto-transfer
                // to inventory, avoiding cursor management issues.
                boolean extracted = p.slotClick(0, 0, "quick_move", 0);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "craft");
                res.addProperty("mode", "recipe");
                res.addProperty("placements", count);
                res.addProperty("extracted", extracted);
                return Result.ok(res);
            }
            case "result": {
                // Just shift-click the result slot (slot 0) — grid is pre-filled
                boolean extracted = p.slotClick(0, 0, "quick_move", 0);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "craft");
                res.addProperty("mode", "result");
                res.addProperty("extracted", extracted);
                return Result.ok(res);
            }
            case "autocraft": {
                // Same as result mode — recipe book has filled the grid
                boolean extracted = p.slotClick(0, 0, "quick_move", 0);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "craft");
                res.addProperty("mode", "autocraft");
                res.addProperty("extracted", extracted);
                return Result.ok(res);
            }
            default:
                throw ProtocolError.badRequest("unknown craft 'mode': " + mode);
        }
    }

    // ---- goto (v26.5-Alpha.1) ----

    /**
     * Navigate to a coordinate or entity. Uses the {@link dev.despotes.common.nav.PathNavigator}
     * which runs on the client thread each tick, driving movement towards the target.
     *
     * <pre>{@code
     * {"type":"goto","x":100,"y":64,"z":200,"stopDistance":2.0}
     * {"type":"goto","uuid":"12345678-...","stopDistance":3.0}
     * }</pre>
     */
    private static Result doGoto(ActionContext ctx, JsonObject cmd) {
        ctx.requireInGame();
        String uuid = Json.getStr(cmd, "uuid", "");
        double stopDist = Json.getDouble(cmd, "stopDistance", 1.5);
        boolean ok;
        if (!uuid.isBlank()) {
            ok = ctx.despotes().navigator().followEntity(uuid, stopDist);
        } else {
            double x = Json.getDouble(cmd, "x", 0);
            double y = Json.getDouble(cmd, "y", 0);
            double z = Json.getDouble(cmd, "z", 0);
            ok = ctx.despotes().navigator().gotoCoords(x, y, z, stopDist);
        }
        JsonObject res = new JsonObject();
        res.addProperty("executed", "goto");
        res.addProperty("started", ok);
        return Result.ok(res);
    }

    // ---- follow (v26.5-Alpha.2) ----

    /**
     * Follow an entity by UUID, maintaining a stop distance.
     *
     * <pre>{@code {"type":"follow","uuid":"12345678-...","stopDistance":3.0}}</pre>
     */
    private static Result doFollow(ActionContext ctx, JsonObject cmd) {
        ctx.requireInGame();
        String uuid = Json.getStr(cmd, "uuid", "");
        if (uuid.isBlank()) {
            throw ProtocolError.badRequest("follow requires 'uuid'");
        }
        double stopDist = Json.getDouble(cmd, "stopDistance", 3.0);
        boolean ok = ctx.despotes().navigator().followEntity(uuid, stopDist);
        JsonObject res = new JsonObject();
        res.addProperty("executed", "follow");
        res.addProperty("started", ok);
        res.addProperty("uuid", uuid);
        return Result.ok(res);
    }

    // ---- whisper (v26.4-Alpha.6) ----

    /**
     * Send a private message to another player via /msg.
     *
     * <pre>{@code {"type":"whisper","target":"PlayerName","message":"hello"}}</pre>
     */
    private static Result doWhisper(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        String target = Json.getStr(cmd, "target", "");
        String message = Json.getStr(cmd, "message", "");
        if (target.isBlank() || message.isBlank()) {
            throw ProtocolError.badRequest("whisper requires 'target' and 'message'");
        }
        p.sendCommand("/msg " + target + " " + message);
        JsonObject res = new JsonObject();
        res.addProperty("executed", "whisper");
        res.addProperty("target", target);
        res.addProperty("chars", message.length());
        return Result.ok(res);
    }

    // ---- trade (v26.3-Alpha.5) ----

    /**
     * Query villager trade offers or execute a trade.
     *
     * <p>Query mode returns the list of offers from the currently open merchant screen.
     * Execute mode clicks the trade result slot to perform the trade.
     *
     * Example (query):
     * <pre>{@code {"type":"trade","mode":"query"}}</pre>
     * Example (execute trade #1):
     * <pre>{@code {"type":"trade","mode":"execute","index":0}}</pre>
     *
     * Requires a merchant screen (villager trading GUI) to be open.
     * In the MerchantMenu, slot 0 is the result slot, slots 1-2 are input slots,
     * and trade selection is done via the merchant's selectTrade method.
     */
    private static Result doTrade(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        String mode = Json.normalize(Json.getStr(cmd, "mode", "query"));

        switch (mode) {
            case "query": {
                // Read the open container's merchant offers reflectively
                JsonObject res = new JsonObject();
                res.addProperty("executed", "trade");
                res.addProperty("mode", "query");
                try {
                    Object mc = Class.forName("net.minecraft.client.Minecraft")
                            .getMethod("getInstance").invoke(null);
                    Object player = mc.getClass().getField("player").get(mc);
                    Object menu = player.getClass().getField("containerMenu").get(player);
                    if (menu != null) {
                        // MerchantMenu has a merchant field with getOffers()
                        try {
                            java.lang.reflect.Field merchantField = null;
                            Class<?> cls = menu.getClass();
                            while (cls != null && merchantField == null) {
                                try {
                                    merchantField = cls.getDeclaredField("merchant");
                                    merchantField.setAccessible(true);
                                } catch (NoSuchFieldException e) { cls = cls.getSuperclass(); }
                            }
                            if (merchantField != null) {
                                Object merchant = merchantField.get(menu);
                                if (merchant != null) {
                                    Object offers = merchant.getClass().getMethod("getOffers").invoke(merchant);
                                    if (offers instanceof java.util.List<?> list) {
                                        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                                        for (Object offer : list) {
                                            JsonObject o = new JsonObject();
                                            try {
                                                Object resultItem = offer.getClass().getMethod("getResult").invoke(offer);
                                                if (resultItem != null) {
                                                    Object item = resultItem.getClass().getMethod("getItem").invoke(resultItem);
                                                    o.addProperty("result", String.valueOf(
                                                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                                                    (net.minecraft.world.item.Item) item)));
                                                    o.addProperty("resultCount", ((Number) resultItem.getClass()
                                                            .getMethod("getCount").invoke(resultItem)).intValue());
                                                }
                                            } catch (Throwable ignored) {}
                                            try {
                                                int uses = ((Number) offer.getClass().getMethod("getUses").invoke(offer)).intValue();
                                                int maxUses = ((Number) offer.getClass().getMethod("getMaxUses").invoke(offer)).intValue();
                                                o.addProperty("uses", uses);
                                                o.addProperty("maxUses", maxUses);
                                            } catch (Throwable ignored) {}
                                            arr.add(o);
                                        }
                                        res.add("offers", arr);
                                        res.addProperty("offerCount", arr.size());
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable t) {
                    res.addProperty("error", "trade query failed: " + t.getMessage());
                }
                return Result.ok(res);
            }
            case "execute": {
                int index = Json.getInt(cmd, "index", 0);
                // Select the trade by index, then click the result slot (slot 2 in MerchantMenu)
                // In MerchantMenu, slot 2 is the result slot
                try {
                    Object mc = Class.forName("net.minecraft.client.Minecraft")
                            .getMethod("getInstance").invoke(null);
                    Object player = mc.getClass().getField("player").get(mc);
                    Object menu = player.getClass().getField("containerMenu").get(player);
                    if (menu != null) {
                        // Try selectTrade(int)
                        try {
                            menu.getClass().getMethod("selectTrade", int.class).invoke(menu, index);
                        } catch (NoSuchMethodException e) {
                            // Fallback: click the trade offer slot directly
                        }
                    }
                } catch (Throwable ignored) {}
                // Click result slot (slot 2 in MerchantMenu) to execute the trade
                boolean ok = p.slotClick(2, 0, "pickup", 0);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "trade");
                res.addProperty("mode", "execute");
                res.addProperty("index", index);
                res.addProperty("dispatched", ok);
                return Result.ok(res);
            }
            default:
                throw ProtocolError.badRequest("unknown trade 'mode': " + mode);
        }
    }

    // ---- sort (v26.3-Alpha.7) ----

    /**
     * Auto-sort the player inventory by quick-moving all main inventory slots.
     * This effectively shift-clicks every slot in the main inventory section,
     * causing items to auto-route to their appropriate sections (hotbar, etc.).
     *
     * <pre>{@code {"type":"sort"}}</pre>
     */
    private static Result doSort(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        // Quick-move (shift-click) each main inventory slot (9-35 in player inventory)
        int count = 0;
        for (int slot = 9; slot <= 35; slot++) {
            if (p.slotClick(slot, 0, "quick_move", 0)) {
                count++;
            }
        }
        JsonObject res = new JsonObject();
        res.addProperty("executed", "sort");
        res.addProperty("moved", count);
        return Result.ok(res);
    }

    // ---- interact (v26.3-Alpha.4) ----

    /**
     * Right-click interact with a block or entity at the crosshair or at specific coordinates.
     *
     * <p>This is a semantic wrapper around {@code useItem} that targets a specific block
     * or entity. It opens doors, toggles levers/buttons, talks to villagers, rides minecarts, etc.
     *
     * <ul>
     *   <li><b>crosshair</b>: interact with whatever the crosshair is over (default)</li>
     *   <li><b>block</b>: interact with a block at x/y/z coordinates (look at it first)</li>
     *   <li><b>entity</b>: interact with an entity by UUID (look at it first)</li>
     * </ul>
     */
    private static Result doInteract(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        String target = Json.normalize(Json.getStr(cmd, "target", "crosshair"));
        String hand = Json.normalize(Json.getStr(cmd, "hand", "main"));

        switch (target) {
            case "crosshair": {
                // Just use item on whatever the crosshair is over
                p.worldUseItem(hand);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "interact");
                res.addProperty("target", "crosshair");
                res.addProperty("hand", hand);
                return Result.ok(res);
            }
            case "block": {
                // Look at the block coordinates first, then use item
                int x = Json.getInt(cmd, "x", 0);
                int y = Json.getInt(cmd, "y", 0);
                int z = Json.getInt(cmd, "z", 0);
                // Use look action to face the block
                var player = p.player();
                if (player != null) {
                    double eyeY = player.y() + 1.62;
                    double dx = x + 0.5 - player.x();
                    double dy = y + 0.5 - eyeY;
                    double dz = z + 0.5 - player.z();
                    double horizontal = Math.sqrt(dx * dx + dz * dz);
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = Math.max(-89.9f, Math.min(89.9f,
                            (float) -Math.toDegrees(Math.atan2(dy, horizontal))));
                    ctx.despotes().lookSmoother().start(yaw, pitch, 0);
                }
                // Use item to interact with the block
                p.worldUseItem(hand);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "interact");
                res.addProperty("target", "block");
                res.addProperty("x", x);
                res.addProperty("y", y);
                res.addProperty("z", z);
                res.addProperty("hand", hand);
                return Result.ok(res);
            }
            case "entity": {
                // Look at the entity first, then use item
                String uuid = Json.getStr(cmd, "uuid", "");
                if (uuid.isBlank()) {
                    throw ProtocolError.badRequest("interact entity requires 'uuid'");
                }
                var player = p.player();
                if (player != null) {
                    JsonObject found = p.findEntity(uuid);
                    if (Json.getBool(found, "found", false)) {
                        double tx = Json.getDouble(found, "x", 0);
                        double ty = Json.getDouble(found, "y", 0);
                        double tz = Json.getDouble(found, "z", 0);
                        double eyeY = player.y() + 1.62;
                        double dx = tx - player.x();
                        double dy = ty - eyeY;
                        double dz = tz - player.z();
                        double horizontal = Math.sqrt(dx * dx + dz * dz);
                        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                        float pitch = Math.max(-89.9f, Math.min(89.9f,
                                (float) -Math.toDegrees(Math.atan2(dy, horizontal))));
                        ctx.despotes().lookSmoother().start(yaw, pitch, 0);
                    }
                }
                p.worldUseItem(hand);
                JsonObject res = new JsonObject();
                res.addProperty("executed", "interact");
                res.addProperty("target", "entity");
                res.addProperty("uuid", uuid);
                res.addProperty("hand", hand);
                return Result.ok(res);
            }
            default:
                throw ProtocolError.badRequest("unknown interact 'target': " + target);
        }
    }

    // ---- equip (v26.3-Alpha.2) ----

    /**
     * Equip or unequip armor. In MC's InventoryMenu the armor slots are:
     * 5=helmet, 6=chestplate, 7=leggings, 8=boots (0-4 are craft/offhand).
     *
     * To equip: pickup the item from sourceSlot, then click the armor slot to place it.
     * To unequip: pickup from armor slot, then click an empty main inventory slot.
     */
    private static Result doEquip(ActionContext ctx, JsonObject cmd) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        String op = Json.normalize(Json.getStr(cmd, "op", "equip"));
        String piece = Json.normalize(Json.getStr(cmd, "piece", ""));

        int armorSlot = switch (piece) {
            case "helmet" -> 5;
            case "chestplate" -> 6;
            case "leggings" -> 7;
            case "boots" -> 8;
            default -> -1;
        };
        if (armorSlot < 0) {
            throw ProtocolError.badRequest("unknown armor 'piece': " + piece
                    + " (expected: helmet, chestplate, leggings, boots)");
        }

        if (op.equals("unequip")) {
            // Pickup from armor slot, then place at a main inventory slot (9 is first main slot)
            p.slotClick(armorSlot, 0, "pickup", 0);
            p.slotClick(9, 0, "pickup", 0);
            JsonObject res = new JsonObject();
            res.addProperty("executed", "equip");
            res.addProperty("op", "unequip");
            res.addProperty("piece", piece);
            res.addProperty("armorSlot", armorSlot);
            res.addProperty("dispatched", true);
            return Result.ok(res);
        }

        // Default: equip — pickup from source, place at armor slot
        int sourceSlot = Json.getInt(cmd, "slot", -1);
        boolean ok = false;
        if (sourceSlot >= 0) {
            boolean ok1 = p.slotClick(sourceSlot, 0, "pickup", 0);
            boolean ok2 = p.slotClick(armorSlot, 0, "pickup", 0);
            ok = ok1 && ok2;
        }

        JsonObject res = new JsonObject();
        res.addProperty("executed", "equip");
        res.addProperty("op", "equip");
        res.addProperty("piece", piece);
        res.addProperty("armorSlot", armorSlot);
        if (sourceSlot >= 0) {
            res.addProperty("sourceSlot", sourceSlot);
        }
        res.addProperty("dispatched", ok);
        return Result.ok(res);
    }

    /**
     * v26.2 death awareness: respawn a dead player. Requires the player to be dead; the
     * platform sends the vanilla respawn packet and closes the death screen. Works while
     * the death screen is open (the normal case).
     */
    private static Result doRespawn(ActionContext ctx) {
        IGamePlatform p = ctx.despotes().platform();
        ctx.requireInGame();
        var player = p.player();
        if (player == null) {
            throw ProtocolError.notInGame();
        }
        if (!player.dead()) {
            throw ProtocolError.badRequest("player is not dead; respawn only applies when dead");
        }
        boolean sent = p.respawn();
        JsonObject res = new JsonObject();
        res.addProperty("executed", "respawn");
        res.addProperty("dispatched", sent);
        res.addProperty("deathScreenOpen", p.deathScreenOpen());
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
        if (p.deathScreenOpen()) {
            res.addProperty("kind", "death");
        }
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
