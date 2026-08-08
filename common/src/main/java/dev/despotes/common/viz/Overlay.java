package dev.despotes.common.viz;

import dev.despotes.common.Despotes;
import dev.despotes.common.config.DespotesConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game overlay state. Rendering itself is performed by the platform layer via
 * {@code IGamePlatform.drawOverlay(List<String>)}; this class only maintains the visible
 * line buffer and the F8 toggle.
 */
public final class Overlay {

    private final DespotesConfig config;
    private volatile boolean visible = true;

    public Overlay(DespotesConfig config) {
        this.config = config;
        this.visible = config.visualization.overlay;
    }

    public boolean visible() {
        return visible;
    }

    public void toggle() {
        visible = !visible;
    }

    /** Builds the current overlay line batch; called on the render thread. */
    public List<String> buildLines(Despotes despotes) {
        List<String> lines = new ArrayList<>();
        if (!visible || !config.visualization.overlay) {
            return lines;
        }
        lines.add("§eDespotes§r queue=" + despotes.dispatcher().queueSize());
        int n = Math.max(1, config.visualization.overlayLines);
        lines.addAll(despotes.opLog().recentLines(n));
        List<String> executing = despotes.dispatcher().executingLines();
        for (String e : executing) {
            lines.add("§a" + e + "§r");
        }
        return lines;
    }
}
