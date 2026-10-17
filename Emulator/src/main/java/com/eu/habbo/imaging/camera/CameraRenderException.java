package com.eu.habbo.imaging.camera;

/** A server render that was refused or failed, with the reason for logs and tests. */
public final class CameraRenderException extends Exception {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        TOO_LARGE,
        INFLATE_FAILED,
        PARSE_FAILED,
        LIMIT_EXCEEDED,
        ROOM_MISMATCH,
        CHECKSUM_FAILED,
        CLOCK_SKEW,
        TIMEOUT,
        RENDER_FAILED,
        WRITE_FAILED
    }

    private final Reason reason;

    public CameraRenderException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CameraRenderException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return this.reason;
    }
}
