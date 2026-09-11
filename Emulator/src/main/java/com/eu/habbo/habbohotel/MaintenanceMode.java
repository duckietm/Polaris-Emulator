package com.eu.habbo.habbohotel;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;

public final class MaintenanceMode {
    public static final String KEY_ENABLED = "hotel.maintenance.enabled";
    public static final String KEY_MESSAGE = "hotel.maintenance.message";
    public static final String KEY_MIN_RANK = "hotel.maintenance.min_rank";

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
