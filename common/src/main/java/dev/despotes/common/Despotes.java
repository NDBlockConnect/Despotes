package dev.despotes.common;

import com.google.gson.JsonObject;
import dev.despotes.common.config.DespotesConfig;
import dev.despotes.common.dispatcher.Dispatcher;
import dev.despotes.common.events.EventBus;
import dev.despotes.common.focus.FocusManager;
import dev.despotes.common.lifecycle.LifeCycleMonitor;
import dev.despotes.common.look.LookSmoother;
import dev.despotes.common.perf.LatencyStats;
import dev.despotes.common.platform.IGamePlatform;
import dev.despotes.common.transport.CliTransport;
import dev.despotes.common.transport.ControlTransport;
import dev.despotes.common.transport.FileDropTransport;
import dev.despotes.common.transport.HttpTransport;
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
    public static final String VERSION = "v26.3-Alpha.9";
    public static final int PROTOCOL_VERSION = 1;

    private static volatile Despotes instance;

    private final IGamePlatform platform;
    private final DespotesConfig config;
    private final Dispatcher dispatcher;
    private final FocusManager focusManager;
    private final LookSmoother lookSmoother;
    private final OpLog opLog;
    private final EventBus eventBus = new EventBus();
    private final Overlay overlay;
    private final LifeCycleMonitor lifeCycle;
    private final LatencyStats latency = new LatencyStats();
    private final List<ControlTransport> transports = new ArrayList<>();
    private final Path configPath;

    private Despotes(IGamePlatform platform, DespotesConfig config, Path configPath) {
        this.platform = platform;
        this.config = config;
        this.configPath = configPath;
        this.focusManager = new FocusManager(platform);
        this.lookSmoother = new LookSmoother(platform);
        this.opLog = new OpLog(config);
        this.overlay = new Overlay(config);
        this.dispatcher = new Dispatcher(this);
        this.lifeCycle = new LifeCycleMonitor(this);
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
        d.startTransports();
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
    }

    /** Called once per client tick on the client thread. */
    public void clientTick() {
        focusManager.tick(config);
        lifeCycle.tick();
        dispatcher.tick();
    }

    /** Called at the end of each rendered frame on the render thread. */
    public void frameEnd() {
        lookSmoother.frameEnd();
        dispatcher.frameEnd();
    }

    public void shutdown() {
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

    /** v26.2-Alpha.6: rolling control-channel latency statistics. */
    public LatencyStats latency() {
        return latency;
    }

    public List<ControlTransport> transports() {
        return transports;
    }

    /** Convenience for transports: builds a JSON status object for this runtime. */
    public JsonObject statusJson() {
        JsonObject o = new JsonObject();
        o.addProperty("despotesVersion", VERSION);
        o.addProperty("protocol", PROTOCOL_VERSION);
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
        o.add("lifecycle", lifeCycle.snapshot());
        o.add("latency", latency.snapshot());
        return o;
    }
}
