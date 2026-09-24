package com.eu.habbo.networking.gameserver.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class WiredApiSettingsTest {
    @Test
    void originsMatchWithOrWithoutSchemeAndSlash() {
        WiredApiSettings settings = withOrigins(WiredApiSettings.origins("example.com, https://www.example.com/"));

        assertEquals("https://example.com", settings.allowedOrigin("https://example.com"));
        assertEquals("http://example.com", settings.allowedOrigin("http://example.com"));
        assertEquals("https://www.example.com", settings.allowedOrigin("https://www.example.com"));
        assertNull(settings.allowedOrigin("http://www.example.com"));
        assertNull(settings.allowedOrigin("https://ws.example.com"));
        assertNull(settings.allowedOrigin("https://example.com.evil.test"));
        assertNull(settings.allowedOrigin("null"));
        assertNull(settings.allowedOrigin(null));
    }

    @Test
    void aWildcardAllowsAnyOrigin() {
        assertEquals("*", withOrigins(List.of("*")).allowedOrigin("https://anything.test"));
    }

    private static WiredApiSettings withOrigins(List<String> origins) {
        WiredApiSettings d = WiredApiSettings.defaults(true);
        return new WiredApiSettings(
                true,
                d.maxPayloadBytes(),
                true,
                d.perIp(),
                d.perIpWindowMs(),
                d.perKey(),
                d.perKeyWindowMs(),
                d.roomWrites(),
                d.roomWritesWindowMs(),
                d.authFailMax(),
                d.authFailWindowMs(),
                d.authFailBlockMs(),
                d.maxBatch(),
                d.maxBulkNames(),
                d.maxPageSize(),
                origins);
    }
}
