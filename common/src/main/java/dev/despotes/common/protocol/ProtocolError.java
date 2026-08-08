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

    public ProtocolError(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static ProtocolError badRequest(String msg) {
        return new ProtocolError(Code.BAD_REQUEST, msg);
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
