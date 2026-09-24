package com.eu.habbo.networking.gameserver.wired;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** One HTTP answer: status, headers and body bytes. */
final class WiredApiResponse {
    private final int status;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final byte[] body;

    WiredApiResponse(int status, String contentType, byte[] body) {
        this.status = status;
        this.body = body == null ? new byte[0] : body;
        if (contentType != null) {
            this.headers.put("Content-Type", contentType);
        }
    }

    static WiredApiResponse json(int status, String json) {
        return new WiredApiResponse(status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    static WiredApiResponse empty(int status) {
        return new WiredApiResponse(status, null, new byte[0]);
    }

    WiredApiResponse header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    int status() {
        return this.status;
    }

    Map<String, String> headers() {
        return this.headers;
    }

    byte[] body() {
        return this.body;
    }

    String bodyText() {
        return new String(this.body, StandardCharsets.UTF_8);
    }
}
