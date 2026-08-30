package dev.despotes.common.protocol;

/** Typed protocol errors mapped to response error codes. */
public final class ProtocolError extends RuntimeException {

    public enum Code {
        BAD_REQUEST,
        UNKNOWN_TYPE,
        NOT_IN_GAME,
        NOT_ON_SCREEN,
        QUEUE_FULL,
        FORBIDDEN,
        TIMEOUT,
        INTERNAL
    }

    private final Code code;

    /** v26.12-Alpha.1: optional structured details (field names, offending values, ...). */
    private final com.google.gson.JsonObject details;

    public ProtocolError(Code code, String message) {
        this(code, message, null);
    }

    public ProtocolError(Code code, String message, com.google.gson.JsonObject details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public Code code() {
        return code;
    }

    /** Structured error details, or null. Emitted as {@code error.details} in responses. */
    public com.google.gson.JsonObject details() {
        return details;
    }

    public static ProtocolError badRequest(String msg) {
        return new ProtocolError(Code.BAD_REQUEST, msg);
    }

    public static ProtocolError badRequest(String msg, com.google.gson.JsonObject details) {
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        return new ProtocolError(Code.BAD_REQUEST, msg, details);
    }

    public static ProtocolError unknownType(String type) {
        return new ProtocolError(Code.UNKNOWN_TYPE, "unknown command type: " + type);
    }

    public static ProtocolError notInGame() {
        return new ProtocolError(Code.NOT_IN_GAME, "not in a game world");
    }

    public static ProtocolError notOnScreen() {
        return new ProtocolError(Code.NOT_ON_SCREEN, "no screen is open");
    }

    public static ProtocolError queueFull() {
        return new ProtocolError(Code.QUEUE_FULL, "command queue is full");
    }

    public static ProtocolError forbidden(String msg) {
        return new ProtocolError(Code.FORBIDDEN, msg);
    }

    public static ProtocolError timeout(String msg) {
        return new ProtocolError(Code.TIMEOUT, msg);
    }

    public static ProtocolError internal(String msg) {
        return new ProtocolError(Code.INTERNAL, msg);
    }
}
