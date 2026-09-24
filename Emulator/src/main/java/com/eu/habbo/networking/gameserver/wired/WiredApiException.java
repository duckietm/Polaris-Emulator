package com.eu.habbo.networking.gameserver.wired;

/** An API answer other than success: the HTTP status and the error code and text of the body. */
final class WiredApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final long retryAfterSeconds;
    private final int limit;

    WiredApiException(int status, String code, String message) {
        this(status, code, message, -1, -1);
    }

    WiredApiException(int status, String code, String message, long retryAfterSeconds, int limit) {
        super(message, null, false, false);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
        this.limit = limit;
    }

    static WiredApiException badRequest(String message) {
        return new WiredApiException(400, "bad_request", message);
    }

    static WiredApiException unauthorized() {
        return new WiredApiException(401, "unauthorized", "Missing or unknown API key.");
    }

    static WiredApiException forbidden(String message) {
        return new WiredApiException(403, "forbidden", message);
    }

    static WiredApiException notFound(String message) {
        return new WiredApiException(404, "not_found", message);
    }

    static WiredApiException conflict(String message) {
        return new WiredApiException(409, "conflict", message);
    }

    static WiredApiException rateLimited(long retryAfterSeconds, int limit) {
        return new WiredApiException(429, "rate_limited", "Too many requests.", Math.max(1, retryAfterSeconds), limit);
    }

    int status() {
        return this.status;
    }

    String code() {
        return this.code;
    }

    long retryAfterSeconds() {
        return this.retryAfterSeconds;
    }

    int limit() {
        return this.limit;
    }
}
