package com.eu.habbo.messages.incoming.rooms.items;

public final class RoomItemInputGuard {
    public static final int MAX_CUSTOM_VALUE_PAIRS = 20;
    public static final int MAX_CUSTOM_KEY_LENGTH = 64;
    public static final int MAX_CUSTOM_VALUE_LENGTH = 512;
    public static final int MAX_LOOK_LENGTH = 512;
    public static final int MAX_YOUTUBE_PLAYLIST_ID_LENGTH = 128;
    public static final int MAX_STICKY_POLE_COMMANDS = 10;
    public static final int MAX_STICKY_POLE_COMMAND_LENGTH = 255;

    private RoomItemInputGuard() {
    }

    public static boolean isPositiveId(int id) {
        return id > 0;
    }

    public static boolean isValidCustomValueCount(int count) {
        return count > 0 && count % 2 == 0 && count / 2 <= MAX_CUSTOM_VALUE_PAIRS;
    }

    public static String trimToMax(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    public static boolean isValidGender(String gender) {
        return "m".equalsIgnoreCase(gender) || "f".equalsIgnoreCase(gender);
    }

    public static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Short parseShort(String value) {
        try {
            return Short.parseShort(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
