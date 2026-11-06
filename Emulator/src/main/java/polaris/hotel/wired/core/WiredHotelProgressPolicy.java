package polaris.hotel.wired.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import polaris.core.ConfigurationManager;

/**
 * The hotel's switch for wired boxes that progress hotel-wide rewards: achievements
 * ({@code hotel.wired.achievements.*}) and reward tracks ({@code hotel.wired.reward_tracks.*}).
 *
 * <p>Off unless the hotel turns it on, and then only for the names on its allow-list. Each user gets
 * at most {@code max_per_window} units per name every {@code window_seconds}, across all rooms.
 */
public final class WiredHotelProgressPolicy {
    public static final String ACHIEVEMENTS = "hotel.wired.achievements";
    public static final String REWARD_TRACKS = "hotel.wired.reward_tracks";

    public static final int DEFAULT_MAX_PER_WINDOW = 50;
    public static final int DEFAULT_WINDOW_SECONDS = 3600;
    public static final int MAX_WINDOW_SECONDS = 7 * 24 * 3600;
    public static final int MAX_ALLOWED_ENTRIES = 500;
    public static final int MAX_ENTRY_LENGTH = 129;

    /** Users one firing progresses at most. */
    public static final int MAX_USERS_PER_FIRING = 50;

    private static final WiredHotelProgressPolicy DISABLED =
            new WiredHotelProgressPolicy(false, Set.of(), DEFAULT_MAX_PER_WINDOW, DEFAULT_WINDOW_SECONDS);

    private final boolean enabled;
    private final Set<String> allowed;
    private final int maxPerWindow;
    private final int windowSeconds;

    WiredHotelProgressPolicy(boolean enabled, Set<String> allowed, int maxPerWindow, int windowSeconds) {
        this.enabled = enabled;
        this.allowed = allowed;
        this.maxPerWindow = maxPerWindow;
        this.windowSeconds = windowSeconds;
    }

    public static WiredHotelProgressPolicy achievements(ConfigurationManager config) {
        return read(config, ACHIEVEMENTS);
    }

    public static WiredHotelProgressPolicy rewardTracks(ConfigurationManager config) {
        return read(config, REWARD_TRACKS);
    }

    static WiredHotelProgressPolicy read(ConfigurationManager config, String prefix) {
        if (config == null || !config.getBoolean(prefix + ".enabled", false)) {
            return DISABLED;
        }

        Set<String> allowed = parseAllowed(config.getValue(prefix + ".allowed", ""));
        int maxPerWindow = Math.max(0, config.getInt(prefix + ".max_per_window", DEFAULT_MAX_PER_WINDOW));
        int windowSeconds =
                Math.clamp(config.getInt(prefix + ".window_seconds", DEFAULT_WINDOW_SECONDS), 1, MAX_WINDOW_SECONDS);

        return new WiredHotelProgressPolicy(
                !allowed.isEmpty() && maxPerWindow > 0, allowed, maxPerWindow, windowSeconds);
    }

    /** A comma, semicolon or whitespace separated list; blank and oversized entries are dropped. */
    static Set<String> parseAllowed(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        Set<String> allowed = new LinkedHashSet<>();
        for (String entry : value.split("[,;\\s]+")) {
            if (allowed.size() >= MAX_ALLOWED_ENTRIES) {
                break;
            }
            if (!entry.isEmpty() && entry.length() <= MAX_ENTRY_LENGTH) {
                allowed.add(entry);
            }
        }

        return Collections.unmodifiableSet(allowed);
    }

    public boolean enabled() {
        return this.enabled;
    }

    public boolean allows(String name) {
        return this.enabled && name != null && this.allowed.contains(name);
    }

    public Set<String> allowed() {
        return this.allowed;
    }

    public int maxPerWindow() {
        return this.maxPerWindow;
    }

    public long windowMs() {
        return this.windowSeconds * 1000L;
    }
}
