package dev.despotes.common;

import com.google.gson.JsonObject;
import dev.despotes.common.config.DespotesConfig;
import dev.despotes.common.dispatcher.Dispatcher;
import dev.despotes.common.events.EventBus;
import dev.despotes.common.focus.FocusManager;
import dev.despotes.common.lifecycle.LifeCycleMonitor;
import dev.despotes.common.look.LookSmoother;
import dev.despotes.common.nav.PathNavigator;
import dev.despotes.common.perf.LatencyStats;
import dev.despotes.common.platform.IGamePlatform;
import dev.despotes.common.schedule.ScheduleManager;
import dev.despotes.common.macro.MacroRecorder;
import dev.despotes.common.transport.CliTransport;
import dev.despotes.common.transport.ControlTransport;
import dev.despotes.common.transport.FileDropTransport;
import dev.despotes.common.transport.HttpTransport;
import dev.despotes.common.transport.WsTransport;
import dev.despotes.common.viz.OpLog;
import dev.despotes.common.viz.Overlay;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Despotes bootstrap and runtime context.
 *
 * <p>Exactly one instance lives inside the Minecraft client process. The loader-specific
 * entrypoint calls {@link #boot(IGamePlatform)} once, then feeds client ticks into
 * {@link #clientTick()} and render-frame ends into {@link #frameEnd()}.
 */
public final class Despotes {

    public static final String MOD_ID = "despotes";
    public static final String VERSION = "v26.12.1";
    public static final int PROTOCOL_VERSION = 1;

    private static volatile Despotes instance;

    private volatile boolean stopped;

    private final IGamePlatform platform;
    private final DespotesConfig config;
    private final Dispatcher dispatcher;
    private final FocusManager focusManager;
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
    private final LookSmoother lookSmoother;
    private final OpLog opLog;
    private final EventBus eventBus = new EventBus();
    private final Overlay overlay;
    private final LifeCycleMonitor lifeCycle;
    private final LatencyStats latency = new LatencyStats();
    private final PathNavigator navigator;
    private final ScheduleManager scheduleManager = new ScheduleManager();
    private final MacroRecorder macroRecorder = new MacroRecorder();
    private final List<ControlTransport> transports = new ArrayList<>();
    private final Path configPath;
    private final String instanceId;
    private final String instanceName;

    private Despotes(IGamePlatform platform, DespotesConfig config, Path configPath) {
        this.platform = platform;
        this.config = config;
        this.configPath = configPath;
        // v26.12.1: stable per-instance identity. Several Despotes instances can run on
        // one machine (and may even share a configured port if one fails to bind), so
        // every response needs to say *which* game it came from — otherwise callers
        // read another instance's state and see phantom / wrong answers.
        this.instanceId = deriveInstanceId(platform);
        this.instanceName = deriveInstanceName(platform);
        this.focusManager = new FocusManager(platform);
        this.lookSmoother = new LookSmoother(platform);
        this.opLog = new OpLog(config);
        this.overlay = new Overlay(config);
        this.dispatcher = new Dispatcher(this);
        this.lifeCycle = new LifeCycleMonitor(this);
        this.navigator = new PathNavigator(this);
    }

    /**
     * Derives a short, stable identifier from the game directory so responses can be
     * attributed to a specific instance. Falls back to {@code unknown} when the
     * platform cannot resolve a game directory.
     *
     * @param platform the loader platform backing this instance
     * @return an 8-hex-character identity, or {@code unknown}
     */
    private static String deriveInstanceId(IGamePlatform platform) {
        try {
            Path dir = platform.gameDir();
            if (dir == null) {
                return "unknown";
            }
            String canonical = dir.toAbsolutePath().normalize().toString();
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /**
     * Best-effort human-readable label for this instance (the game directory's folder
     * name), so operators can match a response to a running game at a glance.
     *
     * @param platform the loader platform backing this instance
     * @return the folder name, or {@code unknown}
     */
    private static String deriveInstanceName(IGamePlatform platform) {
        try {
            Path dir = platform.gameDir();
            if (dir == null) {
                return "unknown";
            }
            Path name = dir.toAbsolutePath().normalize().getFileName();
            return name == null ? "unknown" : name.toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** Boots Despotes. Safe to call multiple times; only the first call has an effect. */
    public static synchronized Despotes boot(IGamePlatform platform) {
        if (instance != null) {
            return instance;
        }
        Path gameDir = platform.gameDir();
        Path configPath = gameDir.resolve("despotes.json");
        DespotesConfig config = DespotesConfig.loadOrCreate(configPath, platform);
        Despotes d = new Despotes(platform, config, configPath);
        instance = d;
        if (!config.window.grabFocusOnStart) {
            // Yield OS focus on start: minimize once so the launcher/user keeps focus.
            try {
                platform.setWindowMinimized(true);
                platform.scheduleOnClientThread(() -> platform.setWindowMinimized(false));
                platform.log("[Despotes] window.grabFocusOnStart=false; not taking focus on start.");
            } catch (Throwable t) {
                platform.log("[Despotes] focus yield on start failed: " + t);
            }
        }
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        d.startTransports();
        // v26.12: register the shutdown hook.
        // NOTE (v26.13 investigation): this hook does NOT rescue the vanilla client
        // shutdown watchdog. Runtime.addShutdownHook only runs once the JVM starts
        // exiting, which requires every non-daemon thread to end first — and the JDK
        // httpserver's internal "HTTP-Dispatcher" thread is non-daemon by construction.
        // The JVM therefore never reaches the hook (verified: crash-report thread dumps
        // carry "DestroyJavaVM" with no "Despotes-Shutdown" thread, and an isolated
        // probe hangs both with and without the hook). The real fix is an application
        // level stop before MC's post-main watchdog runs; tracked in FACT.md.
        Runtime.getRuntime().addShutdownHook(new Thread(d::shutdown, "Despotes-Shutdown"));
        platform.log("[Despotes] " + VERSION + " booted on loader '" + platform.loaderId()
                + "' (MC " + platform.mcVersion() + "). Config: " + configPath);
        return d;
    }

    public static Despotes get() {
        return instance;
    }

    private void startTransports() {
        if (!config.control.enabled) {
            platform.log("[Despotes] control disabled by config; no transports started.");
            return;
        }
        if (config.http.enabled && config.sourceEnabled("http")) {
            HttpTransport http = new HttpTransport();
            transports.add(http);
            http.start(this);
        }
        if (config.cli.enabled && config.sourceEnabled("cli")) {
            CliTransport cli = new CliTransport();
            transports.add(cli);
            cli.start(this);
        }
        if (config.fileDrop.enabled && config.sourceEnabled("filedrop")) {
            FileDropTransport fd = new FileDropTransport();
            transports.add(fd);
            fd.start(this);
        }
        // v26.8: WebSocket transport
        if (config.http.enabled && config.sourceEnabled("ws")) {
            WsTransport ws = new WsTransport();
            transports.add(ws);
            ws.start(this);
        }
    }

    /** Called once per client tick on the client thread. */
    public void clientTick() {
        focusManager.tick(config);
        lifeCycle.tick();
        navigator.tick();
        scheduleManager.tick(this);
        macroRecorder.tick(this);
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        dispatcher.tick();
    }

    /** Called at the end of each rendered frame on the render thread. */
    public void frameEnd() {
        lookSmoother.frameEnd();
        dispatcher.frameEnd();
    }

    public void shutdown() {
        synchronized (this) {
            if (stopped) {
                return;
            }
            stopped = true;
        }
        for (ControlTransport t : transports) {
            try {
                t.stop();
            } catch (Exception e) {
                platform.log("[Despotes] transport stop failed: " + e);
            }
        }
        transports.clear();
        opLog.close();
    }

    public boolean reloadConfig() {
        DespotesConfig fresh = DespotesConfig.loadOrCreate(configPath, platform);
        config.copyFrom(fresh);
        config.save(configPath, platform);
        platform.log("[Despotes] configuration reloaded.");
        return true;
    }

    public IGamePlatform platform() {
        return platform;
    }

    public DespotesConfig config() {
        return config;
    }

    public LookSmoother lookSmoother() {
        return lookSmoother;
    }

    public Dispatcher dispatcher() {
        return dispatcher;
    }

//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
    public OpLog opLog() {
        return opLog;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public Overlay overlay() {
        return overlay;
    }

    public LifeCycleMonitor lifeCycle() {
        return lifeCycle;
    }

    /** v26.5: path navigator for goto/follow actions. */
    public PathNavigator navigator() {
        return navigator;
    }

    /** v26.9: periodic command scheduler. */
    public ScheduleManager scheduleManager() {
        return scheduleManager;
    }

    /** v26.9: action sequence recorder and player. */
    public MacroRecorder macroRecorder() {
        return macroRecorder;
    }

    /** v26.2-Alpha.6: rolling control-channel latency statistics. */
    public LatencyStats latency() {
        return latency;
    }

    /**
     * v26.12.1: stable identity of this running instance, so callers can confirm that a
     * response came from the game they intended to talk to.
     *
     * @return an 8-hex-character id derived from the game directory
     */
    public String instanceId() {
        return instanceId;
    }

    /**
     * v26.12.1: human-readable instance label (game directory folder name).
     *
     * @return the folder name, or {@code unknown}
     */
    public String instanceName() {
        return instanceName;
    }

    /**
     * v26.12.1: absolute path of the game directory this instance controls.
     *
     * @return the normalised game directory path, or an empty string when unavailable
     */
    public String gameDirPath() {
        try {
            Path dir = platform.gameDir();
            return dir == null ? "" : dir.toAbsolutePath().normalize().toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public List<ControlTransport> transports() {
        return transports;
    }

    /** Convenience for transports: builds a JSON status object for this runtime. */
    public JsonObject statusJson() {
        JsonObject o = new JsonObject();
        o.addProperty("despotesVersion", VERSION);
        o.addProperty("protocol", PROTOCOL_VERSION);
        // v26.12.1: instance attribution — lets a caller tell which game answered when
        // several Despotes instances run on one machine.
        o.addProperty("instanceId", instanceId);
        o.addProperty("instanceName", instanceName);
        o.addProperty("gameDir", gameDirPath());
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        o.addProperty("loader", platform.loaderId());
        o.addProperty("mcVersion", platform.mcVersion());
        o.addProperty("inGame", platform.inGame());
        o.addProperty("fps", platform.fps());
        o.addProperty("windowFocused", platform.windowFocused());
        if (platform.inGame() && platform.player() != null) {
            o.add("player", platform.player().statusJson());
        }
        o.addProperty("screenOpen", platform.screen() != null && platform.screen().open());
        o.addProperty("mouseCaptured", platform.isMouseCaptured());
        o.addProperty("queueSize", dispatcher.queueSize());
        // v26.12.1: transport binding diagnostics — surfaces a port clash with another
        // instance instead of leaving the caller to guess why state looks wrong.
        JsonObject transports = new JsonObject();
        JsonObject http = new JsonObject();
        int bound = -1;
        String failure = null;
        for (ControlTransport t : this.transports) {
            if (t instanceof HttpTransport h) {
                bound = h.boundPort();
                failure = h.bindFailure();
            }
        }
        http.addProperty("configuredPort", config.http.port);
        http.addProperty("boundPort", bound);
        http.addProperty("listening", bound > 0);
        if (failure != null) {
            http.addProperty("bindError", failure);
        }
        transports.add("http", http);
        o.add("transports", transports);
        o.add("lifecycle", lifeCycle.snapshot());
        o.add("latency", latency.snapshot());
        return o;
    }
}
