package com.eu.habbo.messages.incoming.users;

final class UserInputGuard {
    static final int MIN_VOLUME = 0;
    static final int MAX_VOLUME = 100;
    static final int MAX_UI_FLAGS = 0xFFFF;

    private UserInputGuard() {
    }

    static boolean isPositiveId(int id) {
        return id > 0;
    }

    static int clampVolume(int volume) {
        return Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, volume));
    }

    static int sanitizeUiFlags(int flags) {
        return flags < 0 ? 0 : flags & MAX_UI_FLAGS;
    }

    static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
