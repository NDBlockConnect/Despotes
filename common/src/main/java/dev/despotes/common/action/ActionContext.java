package dev.despotes.common.action;

import dev.despotes.common.Despotes;
import dev.despotes.common.protocol.ProtocolError;

/** Context handed to action executors on the client thread. */
public final class ActionContext {

    private final Despotes despotes;
    private final String requestId;
    private final String sourceId;
    private final String transport;

    public ActionContext(Despotes despotes, String requestId, String sourceId, String transport) {
        this.despotes = despotes;
        this.requestId = requestId;
        this.sourceId = sourceId;
        this.transport = transport;
    }

    public Despotes despotes() {
        return despotes;
    }

    public String requestId() {
        return requestId;
    }

    public String sourceId() {
        return sourceId;
    }

    public String transport() {
        return transport;
    }

    public void requireInGame() {
        if (!despotes.platform().inGame()) {
            throw ProtocolError.notInGame();
        }
    }

    /** Requires an open screen. Does NOT require a loaded world (menus count). */
    public void requireScreen() {
        if (despotes.platform().screen() == null || !despotes.platform().screen().open()) {
            throw ProtocolError.notOnScreen();
        }
    }
}
