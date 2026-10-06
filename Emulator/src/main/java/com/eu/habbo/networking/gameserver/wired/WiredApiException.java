package com.eu.habbo.networking.gameserver.wired;

/**
 * An API answer other than success: the HTTP status and Habbo's error code, sent as
 * {@code {"error":"<code>"}}. The message is only for the server side; it is never sent.
 */
final class WiredApiException extends RuntimeException {
    // Habbo's codes.
    static final String INVALID_TARGET = "wired.variables.invalid_target";
    static final String INVALID_VALUE = "wired.variables.invalid_value";
    static final String BULK_DELETE_EMPTY = "wired.variables.bulk_delete_empty";
    static final String BULK_DELETE_INVALID_VARIABLE = "wired.variables.bulk_delete_invalid_variable";
    static final String BATCH_EMPTY = "wired.variables.batch_empty";
    static final String BATCH_LIMIT_EXCEEDED = "wired.variables.batch_limit_exceeded";
    static final String KEY_MISSING = "wired.variables.key_missing";
    static final String KEY_INVALID = "wired.variables.key_invalid";
    static final String API_DISABLED = "wired.variables.api_disabled";
    static final String USER_NOT_PARTICIPATING = "wired.variables.user_not_participating";
    static final String BULK_DELETE_NOT_ENABLED = "wired.variables.bulk_delete_not_enabled";
    static final String ROOM_NOT_FOUND = "room.not_found";
    static final String NOT_FOUND = "wired.variables.not_found";
    static final String ENTITY_NOT_FOUND = "wired.variables.entity_not_found";
    static final String TOO_MANY_REQUESTS = "wired.variables.too_many_requests";
    // Polaris additions, for cases Habbo's list has no code for.
    static final String INVALID_REQUEST = "wired.variables.invalid_request";
    static final String BULK_DELETE_LIMIT_EXCEEDED = "wired.variables.bulk_delete_limit_exceeded";
    static final String UNKNOWN_ENDPOINT = "wired.variables.unknown_endpoint";
    static final String METHOD_NOT_ALLOWED = "wired.variables.method_not_allowed";
    static final String PAYLOAD_TOO_LARGE = "wired.variables.payload_too_large";
    static final String CONFLICT = "wired.variables.conflict";
    static final String INTERNAL_ERROR = "wired.variables.internal_error";

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
        return new WiredApiException(400, INVALID_REQUEST, message);
    }

    static WiredApiException invalidTarget(String message) {
        return new WiredApiException(400, INVALID_TARGET, message);
    }

    static WiredApiException invalidValue(String message) {
        return new WiredApiException(400, INVALID_VALUE, message);
    }

    static WiredApiException keyMissing() {
        return new WiredApiException(403, KEY_MISSING, "The key this endpoint needs was not sent.");
    }

    static WiredApiException keyInvalid() {
        return new WiredApiException(403, KEY_INVALID, "Unknown API key.");
    }

    static WiredApiException forbidden(String code, String message) {
        return new WiredApiException(403, code, message);
    }

    static WiredApiException notFound(String message) {
        return new WiredApiException(404, NOT_FOUND, message);
    }

    static WiredApiException entityNotFound() {
        return new WiredApiException(404, ENTITY_NOT_FOUND, "No such holder in this room.");
    }

    static WiredApiException roomNotFound() {
        return new WiredApiException(404, ROOM_NOT_FOUND, "No such room.");
    }

    static WiredApiException unknownEndpoint() {
        return new WiredApiException(404, UNKNOWN_ENDPOINT, "Unknown endpoint.");
    }

    static WiredApiException conflict(String message) {
        return new WiredApiException(409, CONFLICT, message);
    }

    static WiredApiException rateLimited(long retryAfterSeconds, int limit) {
        return new WiredApiException(
                429, TOO_MANY_REQUESTS, "Too many requests.", Math.max(1, retryAfterSeconds), limit);
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
