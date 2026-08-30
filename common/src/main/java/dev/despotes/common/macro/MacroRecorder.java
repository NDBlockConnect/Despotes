package dev.despotes.common.macro;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import dev.despotes.common.action.ActionContext;
import dev.despotes.common.action.Actions;
import dev.despotes.common.protocol.Result;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v26.9-Alpha.1: action sequence recorder and player. Allows external controllers to
 * record a sequence of actions (with tick delays between them) and replay them later.
 *
 * <p>Macros are identified by a string name. Each macro contains a list of steps, where
 * each step has a delay (in ticks) and a command to execute.
 */
public final class MacroRecorder {

    private final Map<String, Macro> macros = new ConcurrentHashMap<>();
    private volatile Macro activeRecording = null;
    private volatile Macro activePlayback = null;
    private int playbackTick = 0;

    public void startRecording(String name) {
        activeRecording = new Macro(name);
    }

    public JsonObject stopRecording() {
        if (activeRecording == null) return null;
        Macro m = activeRecording;
        activeRecording = null;
        macros.put(m.name, m);
        return m.statusJson();
    }

    public void recordStep(JsonObject command) {
        if (activeRecording == null) return;
        MacroStep step = new MacroStep();
        step.delayTicks = activeRecording.steps.isEmpty() ? 0 : 1;
        step.command = command;
        activeRecording.steps.add(step);
    }
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    public boolean play(String name) {
        Macro m = macros.get(name);
        if (m == null) return false;
        activePlayback = m;
        playbackTick = 0;
        return true;
    }

    public void stopPlayback() {
        activePlayback = null;
        playbackTick = 0;
    }

    public boolean delete(String name) {
        return macros.remove(name) != null;
    }

    public Macro get(String name) {
        return macros.get(name);
    }

    public java.util.List<Macro> all() {
        return new java.util.ArrayList<>(macros.values());
    }

    /** Called once per client tick on the client thread. */
    public void tick(Despotes despotes) {
        if (activePlayback == null) return;
        Macro m = activePlayback;
        if (playbackTick >= m.steps.size()) {
            activePlayback = null;
            playbackTick = 0;
            return;
        }
        MacroStep step = m.steps.get(playbackTick);
        if (step.delayTicks > 0) {
            step.delayTicks--;
            return;
        }
        try {
            ActionContext ctx = new ActionContext(despotes, null, "macro", "internal");
            Result r = Actions.execute(ctx, step.command);
            despotes.platform().log("[Macro:" + m.name + " step " + playbackTick + "] " + r.toJsonString(null));
        } catch (Throwable t) {
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
            despotes.platform().log("[Macro:" + m.name + " step " + playbackTick + "] error: " + t.getMessage());
        }
        playbackTick++;
    }

    public JsonObject statusJson() {
        JsonObject o = new JsonObject();
        o.addProperty("macroCount", macros.size());
        o.addProperty("recording", activeRecording != null);
        o.addProperty("playing", activePlayback != null);
        if (activePlayback != null) {
            o.addProperty("playingName", activePlayback.name);
            o.addProperty("playingStep", playbackTick);
            o.addProperty("playingTotalSteps", activePlayback.steps.size());
        }
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (Macro m : macros.values()) {
            arr.add(m.statusJson());
        }
        o.add("macros", arr);
        return o;
    }

    public static final class Macro {
        public final String name;
        public final java.util.List<MacroStep> steps = new java.util.ArrayList<>();

        Macro(String name) {
            this.name = name;
        }

        public JsonObject statusJson() {
            JsonObject o = new JsonObject();
            o.addProperty("name", name);
            o.addProperty("stepCount", steps.size());
            return o;
        }
    }

    public static final class MacroStep {
        public int delayTicks;
        public JsonObject command;
    }
}
