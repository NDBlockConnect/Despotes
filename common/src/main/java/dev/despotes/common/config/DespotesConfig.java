package dev.despotes.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.despotes.common.platform.IGamePlatform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory model of {@code despotes.json}. Field names mirror the documented schema
 * (docs/03-Configuration.md). Missing fields fall back to documented defaults.
 */
public final class DespotesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // control
    public final Control control = new Control();
    // http
    public final Http http = new Http();
    // cli
    public final Cli cli = new Cli();
    // fileDrop
    public final FileDrop fileDrop = new FileDrop();
    // sources
    public final List<SourceEntry> sources = new ArrayList<>();
    // security
    public final Security security = new Security();
    // capture
    public final Capture capture = new Capture();
    // visualization
    public final Visualization visualization = new Visualization();
    // movement
    public final Movement movement = new Movement();
    // focus
    public final Focus focus = new Focus();
    // window
    public final Window window = new Window();

    public int schemaVersion = 1;

    public static final class Control {
        public boolean enabled = true;
        public int maxActionsPerTick = 4;
        public int queueCapacity = 1024;
    }

    public static final class Http {
        public boolean enabled = true;
        public String host = "127.0.0.1";
        public int port = 25585;
        public int maxWorkers = 8;
        public int screenshotTimeoutMs = 5000;
        public int oplogLimit = 200;
    }

    public static final class Cli {
        public boolean enabled = true;
        public boolean echo = true;
    }

    public static final class FileDrop {
        public boolean enabled = false;
        public String dir = "despotes-in";
        public int maxPerTick = 2;
        public boolean deleteAfter = false;
    }

    public static final class SourceEntry {
        public String id = "";
        public String transport = "";
        public boolean enabled = true;
    }

    public static final class Security {
        public boolean enabled = true;
        public boolean requireToken = false;
        public String token = "";
        public final List<String> allowSources = new ArrayList<>();
    }

    public static final class Capture {
        public String dir = "despotes-shots";
        public String format = "png";
        public double jpgQuality = 0.8;
        public int maxWidth = 0;
    }

    public static final class Visualization {
        public boolean overlay = true;
        public int overlayLines = 8;
        public String toggleKey = "key.keyboard.f8";
        public boolean opLog = true;
        public String opLogFile = "despotes-oplog.jsonl";
    }

    public static final class Movement {
        public int defaultLookSmoothTicks = 4;
        /** Frame-driven look easing duration in milliseconds (0 = instant). */
        public int lookSmoothMs = 200;
    }

    /** Window focus policy on launch. */
    public static final class Window {
        /** When false (default), Despotes never grabs OS focus while starting. */
        public boolean grabFocusOnStart = false;
    }

    /** Focus handling: keeps the OS mouse free while the game window is unfocused. */
    public static final class Focus {
        /** Release the captured mouse cursor whenever the game window loses OS focus. */
        public boolean releaseMouseOnFocusLoss = true;
        /** Re-capture the mouse automatically when the window regains OS focus. */
        public boolean regrabMouseOnFocusGain = true;
        /**
         * Prevent vanilla "pause on lost focus" so external control works while the window
         * is unfocused (the game keeps ticking and stays unpaused).
         */
        public boolean preventPauseOnFocusLoss = true;
        /**
         * Continuously keep the mouse released while the window is unfocused, so the game
         * never steals OS focus while you work in another app.
         */
        public boolean keepReleasedWhileUnfocused = true;
    }

    public boolean sourceEnabled(String transport) {
        // If no explicit source entries exist for this transport, fall back to the transport section.
        boolean anyListed = false;
        for (SourceEntry s : sources) {
            if (transport.equalsIgnoreCase(s.transport)) {
                anyListed = true;
                if (s.enabled) {
                    return true;
                }
            }
        }
        return !anyListed;
    }

    public static DespotesConfig loadOrCreate(Path path, IGamePlatform platform) {
        DespotesConfig c = new DespotesConfig();
        if (Files.exists(path)) {
            try {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                c.fromJson(JsonParser.parseString(text).getAsJsonObject());
                platform.log("[Despotes] loaded config from " + path);
            } catch (Exception e) {
                platform.log("[Despotes] failed to parse " + path + " (" + e.getMessage()
                        + "); using defaults and rewriting.");
            }
        }
        c.save(path, platform);
        return c;
    }

    public void save(Path path, IGamePlatform platform) {
        try {
            Files.writeString(path, GSON.toJson(toJson()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            platform.log("[Despotes] failed to write config: " + e);
        }
    }

    /** Replaces mutable state from a freshly parsed config (live reload). */
    public void copyFrom(DespotesConfig o) {
        this.control.enabled = o.control.enabled;
        this.control.maxActionsPerTick = o.control.maxActionsPerTick;
        this.control.queueCapacity = o.control.queueCapacity;
        this.http.enabled = o.http.enabled;
        this.http.host = o.http.host;
        this.http.port = o.http.port;
        this.http.maxWorkers = o.http.maxWorkers;
        this.http.screenshotTimeoutMs = o.http.screenshotTimeoutMs;
        this.http.oplogLimit = o.http.oplogLimit;
        this.cli.enabled = o.cli.enabled;
        this.cli.echo = o.cli.echo;
        this.fileDrop.enabled = o.fileDrop.enabled;
        this.fileDrop.dir = o.fileDrop.dir;
        this.fileDrop.maxPerTick = o.fileDrop.maxPerTick;
        this.fileDrop.deleteAfter = o.fileDrop.deleteAfter;
        this.sources.clear();
        this.sources.addAll(o.sources);
        this.security.enabled = o.security.enabled;
        this.security.requireToken = o.security.requireToken;
        this.security.token = o.security.token;
        this.security.allowSources.clear();
        this.security.allowSources.addAll(o.security.allowSources);
        this.capture.dir = o.capture.dir;
        this.capture.format = o.capture.format;
        this.capture.jpgQuality = o.capture.jpgQuality;
        this.capture.maxWidth = o.capture.maxWidth;
        this.visualization.overlay = o.visualization.overlay;
        this.visualization.overlayLines = o.visualization.overlayLines;
        this.visualization.toggleKey = o.visualization.toggleKey;
        this.visualization.opLog = o.visualization.opLog;
        this.visualization.opLogFile = o.visualization.opLogFile;
        this.movement.defaultLookSmoothTicks = o.movement.defaultLookSmoothTicks;
        this.movement.lookSmoothMs = o.movement.lookSmoothMs;
        this.window.grabFocusOnStart = o.window.grabFocusOnStart;
        this.focus.keepReleasedWhileUnfocused = o.focus.keepReleasedWhileUnfocused;
        this.focus.releaseMouseOnFocusLoss = o.focus.releaseMouseOnFocusLoss;
        this.focus.regrabMouseOnFocusGain = o.focus.regrabMouseOnFocusGain;
        this.focus.preventPauseOnFocusLoss = o.focus.preventPauseOnFocusLoss;
        this.schemaVersion = o.schemaVersion;
    }

    private void fromJson(JsonObject root) {
        schemaVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
        JsonObject o;
        if (root.has("control") && (o = root.getAsJsonObject("control")) != null) {
            control.enabled = bool(o, "enabled", control.enabled);
            control.maxActionsPerTick = integer(o, "maxActionsPerTick", control.maxActionsPerTick);
            control.queueCapacity = integer(o, "queueCapacity", control.queueCapacity);
        }
        if (root.has("http") && (o = root.getAsJsonObject("http")) != null) {
            http.enabled = bool(o, "enabled", http.enabled);
            http.host = str(o, "host", http.host);
            http.port = integer(o, "port", http.port);
            http.maxWorkers = integer(o, "maxWorkers", http.maxWorkers);
            http.screenshotTimeoutMs = integer(o, "screenshotTimeoutMs", http.screenshotTimeoutMs);
            http.oplogLimit = integer(o, "oplogLimit", http.oplogLimit);
        }
        if (root.has("cli") && (o = root.getAsJsonObject("cli")) != null) {
            cli.enabled = bool(o, "enabled", cli.enabled);
            cli.echo = bool(o, "echo", cli.echo);
        }
        if (root.has("fileDrop") && (o = root.getAsJsonObject("fileDrop")) != null) {
            fileDrop.enabled = bool(o, "enabled", fileDrop.enabled);
            fileDrop.dir = str(o, "dir", fileDrop.dir);
            fileDrop.maxPerTick = integer(o, "maxPerTick", fileDrop.maxPerTick);
            fileDrop.deleteAfter = bool(o, "deleteAfter", fileDrop.deleteAfter);
        }
        if (root.has("sources") && root.get("sources").isJsonArray()) {
            sources.clear();
            for (JsonElement e : root.getAsJsonArray("sources")) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject so = e.getAsJsonObject();
                SourceEntry s = new SourceEntry();
                s.id = str(so, "id", s.id);
                s.transport = str(so, "transport", s.transport);
                s.enabled = bool(so, "enabled", s.enabled);
                sources.add(s);
            }
        }
        if (sources.isEmpty()) {
            sources.add(src("local-http", "http", true));
            sources.add(src("local-stdin", "cli", true));
            sources.add(src("local-filedrop", "filedrop", false));
        }
        if (root.has("security") && (o = root.getAsJsonObject("security")) != null) {
            security.enabled = bool(o, "enabled", security.enabled);
            security.requireToken = bool(o, "requireToken", security.requireToken);
            security.token = str(o, "token", security.token);
            if (o.has("allowSources") && o.get("allowSources").isJsonArray()) {
                security.allowSources.clear();
                for (JsonElement e : o.getAsJsonArray("allowSources")) {
                    security.allowSources.add(e.getAsString());
                }
            }
        }
        if (root.has("capture") && (o = root.getAsJsonObject("capture")) != null) {
            capture.dir = str(o, "dir", capture.dir);
            capture.format = str(o, "format", capture.format);
            capture.jpgQuality = dbl(o, "jpgQuality", capture.jpgQuality);
            capture.maxWidth = integer(o, "maxWidth", capture.maxWidth);
        }
        if (root.has("visualization") && (o = root.getAsJsonObject("visualization")) != null) {
            visualization.overlay = bool(o, "overlay", visualization.overlay);
            visualization.overlayLines = integer(o, "overlayLines", visualization.overlayLines);
            visualization.toggleKey = str(o, "toggleKey", visualization.toggleKey);
            visualization.opLog = bool(o, "opLog", visualization.opLog);
            visualization.opLogFile = str(o, "opLogFile", visualization.opLogFile);
        }
        if (root.has("movement") && (o = root.getAsJsonObject("movement")) != null) {
            movement.defaultLookSmoothTicks = integer(o, "defaultLookSmoothTicks", movement.defaultLookSmoothTicks);
            movement.lookSmoothMs = integer(o, "lookSmoothMs", movement.lookSmoothMs);
        }
        if (root.has("window") && (o = root.getAsJsonObject("window")) != null) {
            window.grabFocusOnStart = bool(o, "grabFocusOnStart", window.grabFocusOnStart);
        }
        if (root.has("focus") && (o = root.getAsJsonObject("focus")) != null) {
            focus.keepReleasedWhileUnfocused = bool(o, "keepReleasedWhileUnfocused", focus.keepReleasedWhileUnfocused);
        }
        if (root.has("focus") && (o = root.getAsJsonObject("focus")) != null) {
            focus.releaseMouseOnFocusLoss = bool(o, "releaseMouseOnFocusLoss", focus.releaseMouseOnFocusLoss);
            focus.regrabMouseOnFocusGain = bool(o, "regrabMouseOnFocusGain", focus.regrabMouseOnFocusGain);
            focus.preventPauseOnFocusLoss = bool(o, "preventPauseOnFocusLoss", focus.preventPauseOnFocusLoss);
            focus.keepReleasedWhileUnfocused = bool(o, "keepReleasedWhileUnfocused", focus.keepReleasedWhileUnfocused);
        }
    }

    private static SourceEntry src(String id, String transport, boolean enabled) {
        SourceEntry s = new SourceEntry();
        s.id = id;
        s.transport = transport;
        s.enabled = enabled;
        return s;
    }

    private JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", schemaVersion);

        JsonObject c = new JsonObject();
        c.addProperty("enabled", control.enabled);
        c.addProperty("maxActionsPerTick", control.maxActionsPerTick);
        c.addProperty("queueCapacity", control.queueCapacity);
        root.add("control", c);

        JsonObject h = new JsonObject();
        h.addProperty("enabled", http.enabled);
        h.addProperty("host", http.host);
        h.addProperty("port", http.port);
        h.addProperty("maxWorkers", http.maxWorkers);
        h.addProperty("screenshotTimeoutMs", http.screenshotTimeoutMs);
        h.addProperty("oplogLimit", http.oplogLimit);
        root.add("http", h);

        JsonObject cl = new JsonObject();
        cl.addProperty("enabled", cli.enabled);
        cl.addProperty("echo", cli.echo);
        root.add("cli", cl);

        JsonObject f = new JsonObject();
        f.addProperty("enabled", fileDrop.enabled);
        f.addProperty("dir", fileDrop.dir);
        f.addProperty("maxPerTick", fileDrop.maxPerTick);
        f.addProperty("deleteAfter", fileDrop.deleteAfter);
        root.add("fileDrop", f);

        JsonArray arr = new JsonArray();
        for (SourceEntry s : sources) {
            JsonObject so = new JsonObject();
            so.addProperty("id", s.id);
            so.addProperty("transport", s.transport);
            so.addProperty("enabled", s.enabled);
            arr.add(so);
        }
        root.add("sources", arr);

        JsonObject sec = new JsonObject();
        sec.addProperty("enabled", security.enabled);
        sec.addProperty("requireToken", security.requireToken);
        sec.addProperty("token", security.token);
        JsonArray allow = new JsonArray();
        security.allowSources.forEach(allow::add);
        sec.add("allowSources", allow);
        root.add("security", sec);

        JsonObject cap = new JsonObject();
        cap.addProperty("dir", capture.dir);
        cap.addProperty("format", capture.format);
        cap.addProperty("jpgQuality", capture.jpgQuality);
        cap.addProperty("maxWidth", capture.maxWidth);
        root.add("capture", cap);

        JsonObject v = new JsonObject();
        v.addProperty("overlay", visualization.overlay);
        v.addProperty("overlayLines", visualization.overlayLines);
        v.addProperty("toggleKey", visualization.toggleKey);
        v.addProperty("opLog", visualization.opLog);
        v.addProperty("opLogFile", visualization.opLogFile);
        root.add("visualization", v);

        JsonObject m = new JsonObject();
        m.addProperty("defaultLookSmoothTicks", movement.defaultLookSmoothTicks);
        m.addProperty("lookSmoothMs", movement.lookSmoothMs);
        root.add("movement", m);

        JsonObject w = new JsonObject();
        w.addProperty("grabFocusOnStart", window.grabFocusOnStart);
        root.add("window", w);

        JsonObject fo = new JsonObject();
        fo.addProperty("releaseMouseOnFocusLoss", focus.releaseMouseOnFocusLoss);
        fo.addProperty("regrabMouseOnFocusGain", focus.regrabMouseOnFocusGain);
        fo.addProperty("preventPauseOnFocusLoss", focus.preventPauseOnFocusLoss);
        root.add("focus", fo);

        return root;
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsBoolean() : def;
    }

    private static int integer(JsonObject o, String k, int def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
    }

    private static double dbl(JsonObject o, String k, double def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }
}
