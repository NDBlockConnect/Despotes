package dev.despotes.common.dispatcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import dev.despotes.common.action.ActionContext;
import dev.despotes.common.action.Actions;
import dev.despotes.common.protocol.Json;
import dev.despotes.common.protocol.ProtocolError;
import dev.despotes.common.protocol.Result;
import dev.despotes.common.viz.OpEntry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Command queue and client-thread executor.
 *
 * <p>Execution model: transport worker threads {@link #submit(Command)} a command and
 * block on the returned future; the client-thread {@link #tick()} drains up to
 * {@code control.maxActionsPerTick} commands per tick, executes them, and completes the
 * futures with the real execution results. Queries are routed inline (read-only snapshot
 * on the client thread) and never enter the queue.
 */
public final class Dispatcher {

    /** A queued command with its provenance. */
    public static final class Command {
        public final JsonObject json;
        public final String requestId;
        public final String sourceId;
        public final String transport;
        public final CompletableFuture<String> response = new CompletableFuture<>();
        /** v26.2-Alpha.6: enqueue time, used to measure queue wait latency. */
        public final long enqueueNanos = System.nanoTime();

        public Command(JsonObject json, String requestId, String sourceId, String transport) {
            this.json = json;
            this.requestId = requestId == null ? "" : requestId;
            this.sourceId = sourceId;
            this.transport = transport;
        }
    }

    private record Scheduled(int dueTick, Runnable task) {
    }

    private final Despotes despotes;
    private final LinkedBlockingQueue<Command> queue;
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final List<String> executing = new ArrayList<>();
    private int tickCount;

    public Dispatcher(Despotes despotes) {
        this.despotes = despotes;
        this.queue = new LinkedBlockingQueue<>(Math.max(8, despotes.config().control.queueCapacity));
    }

    /**
     * Transport-side entry point. Enqueues the command and blocks up to {@code waitMs} for
     * the client-thread execution result. Returns the response envelope JSON.
     */
    public String submit(Command cmd, long waitMs) {
        if (!despotes.config().control.enabled) {
            return Json.error(cmd.requestId, ProtocolError.forbidden("control is disabled"));
        }
        if (!queue.offer(cmd)) {
            return Json.error(cmd.requestId, ProtocolError.queueFull());
        }
        try {
            return cmd.response.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return Json.error(cmd.requestId,
                    ProtocolError.timeout("command not executed within " + waitMs + "ms"));
        }
    }

    /** Client-thread tick: run scheduled continuations, then drain queued commands. */
    public void tick() {
        tickCount++;

        if (!scheduled.isEmpty()) {
            Iterator<Scheduled> it = scheduled.iterator();
            while (it.hasNext()) {
                Scheduled s = it.next();
                if (s.dueTick() <= tickCount) {
                    it.remove();
                    try {
                        s.task().run();
                    } catch (Throwable t) {
                        despotes.platform().log("[Despotes] scheduled task failed: " + t);
                    }
                }
            }
        }

        int budget = Math.max(1, despotes.config().control.maxActionsPerTick);
        List<Command> batch = new ArrayList<>();
        queue.drainTo(batch, budget);
        executing.clear();
        for (Command cmd : batch) {
            String type = Json.getStr(cmd.json, "type", "?");
            long start = System.nanoTime();
            // v26.2-Alpha.6: time spent waiting in the queue before the client thread
            // picked this command up (queue contention / tick-budget saturation).
            long waitedUs = (start - cmd.enqueueNanos) / 1000;

            // Screenshot runs on the async GPU-copy path: the response future is
            // completed from the capture callback, never on this tick.
            if (type.equals("screenshot")) {
                try {
                    ActionContext ctx = new ActionContext(despotes, cmd.requestId, cmd.sourceId, cmd.transport);
                    Actions.executeScreenshotAsync(ctx, cmd.json, result -> {
                        long durUs = (System.nanoTime() - start) / 1000;
                        executing.add(String.format("[%s#%s] %s %s (%dus)", cmd.transport, cmd.requestId,
                                "screenshot", result.ok() ? "ok" : "ERR:" + result.error().code(), durUs));
                        despotes.opLog().record(new OpEntry(cmd.sourceId, cmd.transport, cmd.requestId,
                                "screenshot", result, durUs));
                        cmd.response.complete(result.toJsonString(cmd.requestId));
                    });
                } catch (Throwable t) {
                    Result r = Result.fail(ProtocolError.internal(String.valueOf(t.getMessage())));
                    cmd.response.complete(r.toJsonString(cmd.requestId));
                }
                continue;
            }

            Result result;
            try {
                ActionContext ctx = new ActionContext(despotes, cmd.requestId, cmd.sourceId, cmd.transport);
                result = Actions.execute(ctx, cmd.json);
            } catch (ProtocolError e) {
                result = Result.fail(e);
            } catch (Throwable t) {
                result = Result.fail(ProtocolError.internal(String.valueOf(t.getMessage())));
                despotes.platform().log("[Despotes] command '" + type + "' failed: " + t);
            }
            long durUs = (System.nanoTime() - start) / 1000;
            // v26.2-Alpha.6: feed the rolling latency statistics.
            despotes.latency().record(waitedUs, durUs);
            executing.add(String.format("[%s#%s] %s %s (wait %dus, exec %dus)", cmd.transport, cmd.requestId,
                    type, result.ok() ? "ok" : "ERR:" + result.error().code(), waitedUs, durUs));
            despotes.opLog().record(new OpEntry(cmd.sourceId, cmd.transport, cmd.requestId,
                    type, result, durUs));
            cmd.response.complete(result.toJsonString(cmd.requestId, waitedUs, durUs));
        }
    }

    /** Render-frame hook, reserved for frame-bound work. */
    public void frameEnd() {
    }

    /** Run a task after {@code ticks} client ticks. Must be called on the client thread. */
    public void scheduleInTicks(int ticks, Runnable task) {
        scheduled.add(new Scheduled(tickCount + Math.max(1, ticks), task));
    }

    public int queueSize() {
        return queue.size();
    }

    public JsonArray executingJson() {
        JsonArray a = new JsonArray();
        for (String s : executing) {
            a.add(s);
        }
        return a;
    }

    public List<String> executingLines() {
        return executing;
    }
}
