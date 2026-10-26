package com.eu.habbo.habbohotel.items;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The settings a room background (room ads furni) may store. The save used to take any key and any address, so a
 * background could load an image from any website and leak every visitor's address to it. Only the known keys are
 * kept, numbers are clamped, addresses must be http(s), and the image may only come from the allowed hosts.
 */
public final class RoomBackgroundSettings {
    public static final String ALLOWED_HOSTS_SETTING = "room.background.allowed_hosts";

    private static final String IMAGE_URL = "imageUrl";
    private static final String CLICK_URL = "clickUrl";

    private static final java.util.Set<String> KNOWN_KEYS =
            java.util.Set.of(IMAGE_URL, CLICK_URL, "offsetX", "offsetY", "offsetZ", "scale", "alpha");

    private RoomBackgroundSettings() {}

    public static boolean isKnownKey(String key) {
        return KNOWN_KEYS.contains(key);
    }

    /** The stored settings, or null when the save must be refused (a value that is no number, or a refused address). */
    public static Map<String, String> sanitize(Map<String, String> input, String allowedHosts) {
        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();

            switch (key) {
                case IMAGE_URL -> {
                    if (!isAllowedAddress(value) || !isAllowedHost(value, allowedHosts)) return null;
                    result.put(key, value);
                }
                case CLICK_URL -> {
                    if (!isAllowedAddress(value)) return null;
                    result.put(key, value);
                }
                case "offsetX", "offsetY" -> {
                    Integer number = clamp(value, -10000, 10000);
                    if (number == null) return null;
                    result.put(key, number.toString());
                }
                case "offsetZ" -> {
                    Integer number = clamp(value, -1000, 1000);
                    if (number == null) return null;
                    result.put(key, number.toString());
                }
                case "scale" -> {
                    Integer number = clamp(value, 0, 500);
                    if (number == null) return null;
                    result.put(key, number.toString());
                }
                case "alpha" -> {
                    Integer number = clamp(value, 0, 100);
                    if (number == null) return null;
                    result.put(key, number.toString());
                }
                default -> {
                    // Unknown keys are dropped.
                }
            }
        }

        return result;
    }

    /** Empty, or an http(s) address. */
    static boolean isAllowedAddress(String value) {
        if (value.isEmpty()) return true;

        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();

            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
        } catch (Exception exception) {
            return false;
        }
    }

    /** The comma-separated hosts (subdomains included); an empty list allows any website. */
    static boolean isAllowedHost(String value, String allowedHosts) {
        if (value.isEmpty() || allowedHosts == null || allowedHosts.isBlank()) return true;

        String host;
        try {
            host = new URI(value).getHost().toLowerCase(Locale.ROOT);
        } catch (Exception exception) {
            return false;
        }

        for (String allowed : allowedHosts.split(",")) {
            String entry = allowed.trim().toLowerCase(Locale.ROOT);

            if (entry.isEmpty()) continue;
            if (host.equals(entry) || host.endsWith("." + entry)) return true;
        }

        return false;
    }

    /** An empty number is 0; anything that is not a whole number refuses the save. */
    private static Integer clamp(String value, int min, int max) {
        if (value.isEmpty()) return 0;

        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
