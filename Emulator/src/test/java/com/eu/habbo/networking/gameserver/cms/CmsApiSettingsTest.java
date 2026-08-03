package com.eu.habbo.networking.gameserver.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CmsApiSettingsTest {

    /** Fallback that just echoes the caller's default, so table hits are distinguishable. */
    private static final BiFunction<String, String, String> ECHO_DEFAULT = (key, def) -> def;

    private static CmsApiSettings settings(Map<String, String> table) {
        return new CmsApiSettings(() -> table, ECHO_DEFAULT, 1000, () -> 0L);
    }

    @Test
    void tableValuesWinOverDefaults() {
        CmsApiSettings settings = settings(Map.of(
                "cms.api.max_payload_bytes", "4096",
                "cms.api.require_tls", "1",
                "cms.api.keys", "cms|secret|*"));

        assertEquals(4096, settings.getInt("cms.api.max_payload_bytes", 65536));
        assertTrue(settings.getBoolean("cms.api.require_tls", false));
        assertEquals("cms|secret|*", settings.getValue("cms.api.keys", ""));
    }

    @Test
    void missingRowFallsBackToDefault() {
        CmsApiSettings settings = settings(Map.of());
        assertEquals(300, settings.getInt("cms.api.timestamp.skew.seconds", 300));
        assertEquals("", settings.getValue("cms.api.keys", ""));
        assertFalse(settings.getBoolean("cms.api.require_tls", false));
    }

    @Test
    void booleanAcceptsSeveralSpellings() {
        CmsApiSettings settings =
                settings(Map.of("t1", "1", "t2", "true", "t3", "TRUE", "f1", "0", "f2", "false", "junk", "maybe"));
        assertTrue(settings.getBoolean("t1", false));
        assertTrue(settings.getBoolean("t2", false));
        assertTrue(settings.getBoolean("t3", false));
        assertFalse(settings.getBoolean("f1", true));
        assertFalse(settings.getBoolean("f2", true));
        // Unparseable value falls back to the caller default rather than throwing.
        assertTrue(settings.getBoolean("junk", true));
    }

    @Test
    void nonIntegerValueFallsBackToDefault() {
        CmsApiSettings settings = settings(new HashMap<>(Map.of("cms.api.max_payload_bytes", "not-a-number")));
        assertEquals(65536, settings.getInt("cms.api.max_payload_bytes", 65536));
    }

    @Test
    void loaderIsCachedWithinTtlAndRefreshedAfter() {
        AtomicInteger loads = new AtomicInteger();
        long[] now = {0L};
        Supplier<Map<String, String>> loader = () -> {
            loads.incrementAndGet();
            return Map.of("cms.api.keys", "v" + loads.get());
        };
        CmsApiSettings settings = new CmsApiSettings(loader, ECHO_DEFAULT, 1000, (LongSupplier) () -> now[0]);

        assertEquals("v1", settings.getValue("cms.api.keys", ""));
        now[0] = 500; // within TTL
        assertEquals("v1", settings.getValue("cms.api.keys", ""));
        assertEquals(1, loads.get());

        now[0] = 1500; // past TTL -> reload
        assertEquals("v2", settings.getValue("cms.api.keys", ""));
        assertEquals(2, loads.get());
    }

    @Test
    void loaderFailureFallsBackToDefaults() {
        CmsApiSettings settings = new CmsApiSettings(
                () -> {
                    throw new RuntimeException("db down");
                },
                ECHO_DEFAULT,
                1000,
                () -> 0L);

        assertEquals(300, settings.getInt("cms.api.timestamp.skew.seconds", 300));
    }
}
