package com.eu.habbo.habbohotel;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;

public final class MaintenanceMode {
    public static final String KEY_ENABLED = "hotel.maintenance.enabled";
    public static final String KEY_MESSAGE = "hotel.maintenance.message";
    public static final String KEY_MIN_RANK = "hotel.maintenance.min_rank";

    /** When the hotel is expected back, so the client can say so instead of "later". */
    public static final String KEY_REOPEN_HOUR = "hotel.maintenance.reopen_hour";

    public static final String KEY_REOPEN_MINUTE = "hotel.maintenance.reopen_minute";

    public static final String DEFAULT_MESSAGE =
            "The hotel is currently undergoing maintenance. Please try again later.";
    public static final int DEFAULT_MIN_RANK = 5;

    public static final int MAX_MESSAGE_LENGTH = 200;

    private MaintenanceMode() {}

    public static boolean isEnabled() {
        return Emulator.getConfig().getBoolean(KEY_ENABLED, false);
    }

    public static String getMessage() {
        String message = Emulator.getConfig().getValue(KEY_MESSAGE, DEFAULT_MESSAGE);
        return (message == null || message.isBlank()) ? DEFAULT_MESSAGE : message;
    }

    public static int getMinRank() {
        return Emulator.getConfig().getInt(KEY_MIN_RANK, DEFAULT_MIN_RANK);
    }

    /** The hour the hotel says it will be back, or -1 when nobody has said. */
    public static int getReopenHour() {
        return Emulator.getConfig().getInt(KEY_REOPEN_HOUR, -1);
    }

    public static int getReopenMinute() {
        return Math.max(0, Math.min(59, Emulator.getConfig().getInt(KEY_REOPEN_MINUTE, 0)));
    }

    /** Whether there is a time worth telling anybody. */
    public static boolean hasReopenTime() {
        int hour = getReopenHour();

        return hour >= 0 && hour <= 23;
    }

    /** Sets when the hotel is expected back; an hour outside the clock clears it. */
    public static synchronized void setReopenTime(int hour, int minute) {
        ConfigurationManager config = Emulator.getConfig();
        config.register(KEY_REOPEN_HOUR, "-1");
        config.register(KEY_REOPEN_MINUTE, "0");

        boolean valid = hour >= 0 && hour <= 23;

        config.update(KEY_REOPEN_HOUR, Integer.toString(valid ? hour : -1));
        config.update(KEY_REOPEN_MINUTE, Integer.toString(valid ? Math.max(0, Math.min(59, minute)) : 0));
        config.saveToDatabase();
    }

    public static boolean canLogin(int rankId) {
        return !isEnabled() || rankId >= getMinRank();
    }

    public static synchronized void setEnabled(boolean enabled, String message) {
        ConfigurationManager config = Emulator.getConfig();
        config.register(KEY_ENABLED, "0");
        config.register(KEY_MESSAGE, DEFAULT_MESSAGE);
        config.register(KEY_MIN_RANK, Integer.toString(DEFAULT_MIN_RANK));

        config.update(KEY_ENABLED, enabled ? "1" : "0");

        if (message != null && !message.isBlank()) {
            String trimmed = message.trim();
            if (trimmed.length() > MAX_MESSAGE_LENGTH) trimmed = trimmed.substring(0, MAX_MESSAGE_LENGTH);
            config.update(KEY_MESSAGE, trimmed);
        }

        config.saveToDatabase();
    }
}
