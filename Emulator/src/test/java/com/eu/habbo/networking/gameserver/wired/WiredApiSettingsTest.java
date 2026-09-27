package com.eu.habbo.networking.gameserver.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class WiredApiSettingsTest {
    @Test
    void originsMatchWithOrWithoutSchemeAndSlash() {
        WiredApiSettings settings = withOrigins(WiredApiSettings.origins("habbo.com, https://www.habbo.com/"));

        assertEquals("https://habbo.com", settings.allowedOrigin("https://habbo.com"));
        assertEquals("http://habbo.com", settings.allowedOrigin("http://habbo.com"));
        assertEquals("https://www.habbo.com", settings.allowedOrigin("https://www.habbo.com"));
        assertNull(settings.allowedOrigin("http://www.habbo.com"));
        assertNull(settings.allowedOrigin("https://ws.habbo.com"));
        assertNull(settings.allowedOrigin("https://habbo.com.evil.test"));
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
