package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

class RoomBackgroundSettingsTest {
    private static Map<String, String> pairs(String... values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) map.put(values[i], values[i + 1]);
        return map;
    }

    @Test
    void keepsTheKnownKeysAndClampsTheNumbers() {
        Map<String, String> result = RoomBackgroundSettings.sanitize(
                pairs(
                        "imageUrl",
                        "",
                        "offsetX",
                        "99999",
                        "offsetY",
                        "-99999",
                        "offsetZ",
                        "5000",
                        "scale",
                        "9999",
                        "alpha",
                        "-5",
                        "extra",
                        "1",
                        "state",
                        "7"),
                "");

        assertEquals(
                pairs(
                        "imageUrl",
                        "",
                        "offsetX",
                        "10000",
                        "offsetY",
                        "-10000",
                        "offsetZ",
                        "1000",
                        "scale",
                        "500",
                        "alpha",
                        "0"),
                result);
    }

    @Test
    void readsAnEmptyNumberAsZeroAndRefusesANonNumber() {
        assertEquals(
                pairs("offsetX", "0", "offsetY", "4"),
                RoomBackgroundSettings.sanitize(pairs("offsetX", "", "offsetY", " 4 "), ""));
        assertNull(RoomBackgroundSettings.sanitize(pairs("offsetX", "abc"), ""));
        assertNull(RoomBackgroundSettings.sanitize(pairs("offsetY", "1.5"), ""));
    }

    @Test
    void takesOnlyHttpAddresses() {
        assertNull(RoomBackgroundSettings.sanitize(pairs("clickUrl", "javascript:alert(1)"), ""));
        assertNull(RoomBackgroundSettings.sanitize(pairs("imageUrl", "file:///etc/passwd"), ""));
        assertEquals(
                pairs("imageUrl", "https://images.example.com/a.png"),
                RoomBackgroundSettings.sanitize(pairs("imageUrl", "https://images.example.com/a.png"), ""));
    }

    @Test
    void limitsTheImageToTheAllowedHostsAndTheirSubdomains() {
        String hosts = "example.com, habbo.com";

        assertTrue(RoomBackgroundSettings.isAllowedHost("https://cdn.example.com/a.png", hosts));
        assertTrue(RoomBackgroundSettings.isAllowedHost("https://habbo.com/a.png", hosts));
        assertFalse(RoomBackgroundSettings.isAllowedHost("https://evil-example.com/a.png", hosts));
        assertFalse(RoomBackgroundSettings.isAllowedHost("https://tracker.net/a.png", hosts));
        assertNull(RoomBackgroundSettings.sanitize(pairs("imageUrl", "https://tracker.net/a.png"), hosts));
        assertTrue(RoomBackgroundSettings.isAllowedHost("https://tracker.net/a.png", ""));
    }

    @Test
    void theOpenDefaultIsReportedToTheOperatorOnce() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/items/RoomBackgroundSettings.java"));

        assertTrue(source.contains("if (OPEN_DEFAULT_REPORTED.compareAndSet(false, true)) {"));
        assertTrue(RoomBackgroundSettings.isAllowedHost("https://tracker.net/b.png", " "));
    }
}
