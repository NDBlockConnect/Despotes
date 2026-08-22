package dev.despotes.common.schedule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import dev.despotes.common.action.ActionContext;
import dev.despotes.common.action.Actions;
import dev.despotes.common.protocol.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * v26.9-Alpha.1: periodic command scheduler. Allows external controllers to register
 * repeating command sequences that execute automatically on the game tick.
 *
 * <p>Schedules are identified by a string name. Each schedule has a period (in ticks)
 * and a list of commands to execute. The scheduler runs on the client thread during
 * {@link Despotes#clientTick()}.
 */
public final class ScheduleManager {

    private final Map<String, Schedule> schedules = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public long add(String name, int periodTicks, JsonArray commands) {
        long id = nextId.getAndIncrement();
        schedules.put(name, new Schedule(id, name, periodTicks, commands));
        return id;
    }

    public boolean remove(String name) {
        return schedules.remove(name) != null;
    }

    public void clear() {
        schedules.clear();
    }

    public Schedule get(String name) {
        return schedules.get(name);
    }

    public List<Schedule> all() {
        return new ArrayList<>(schedules.values());
    }

    /** Called once per client tick on the client thread. */
    public void tick(Despotes despotes) {
        for (Schedule s : schedules.values()) {
            s.tickCount++;
            if (s.tickCount >= s.periodTicks) {
                s.tickCount = 0;
                s.executionCount++;
                for (var cmdEl : s.commands) {
                    if (!cmdEl.isJsonObject()) continue;
                    JsonObject cmd = cmdEl.getAsJsonObject();
                    try {
                        ActionContext ctx = new ActionContext(despotes, null, "scheduler", "internal");
                        Result r = Actions.execute(ctx, cmd);
                        despotes.platform().log("[Schedule:" + s.name + "] " + r.toJsonString(null));
                    } catch (Throwable t) {
                        despotes.platform().log("[Schedule:" + s.name + "] error: " + t.getMessage());
                    }
                }
            }
        }
    }

    public JsonObject statusJson() {
        JsonObject o = new JsonObject();
        o.addProperty("count", schedules.size());
        JsonArray arr = new JsonArray();
        for (Schedule s : schedules.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", s.name);
            j.addProperty("id", s.id);
            j.addProperty("periodTicks", s.periodTicks);
            j.addProperty("commandCount", s.commands.size());
            j.addProperty("executionCount", s.executionCount);
            j.addProperty("nextRunIn", s.periodTicks - s.tickCount);
            arr.add(j);
        }
        o.add("schedules", arr);
        return o;
    }

    public static final class Schedule {
        public final long id;
        public final String name;
        public final int periodTicks;
        public final JsonArray commands;
        public int tickCount = 0;
        public int executionCount = 0;

        Schedule(long id, String name, int periodTicks, JsonArray commands) {
            this.id = id;
            this.name = name;
            this.periodTicks = periodTicks;
            this.commands = commands;
        }
    }
}
