package dev.despotes.common.transport;

import dev.despotes.common.Despotes;

/** A control source transport (http / cli / filedrop / plugin). */
public interface ControlTransport {

    /** Transport identifier used in source tagging: "http" | "cli" | "filedrop" | "plugin". */
    String id();

    void start(Despotes despotes);

    void stop();
}
