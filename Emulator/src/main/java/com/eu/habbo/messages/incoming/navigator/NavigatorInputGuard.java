package com.eu.habbo.messages.incoming.navigator;

final class NavigatorInputGuard {
    static final int MAX_SEARCH_LENGTH = 64;
    static final int MAX_SAVED_SEARCH_LENGTH = 255;

    private NavigatorInputGuard() {
    }

    static String normalizeSearch(String value) {
        return normalize(value, MAX_SEARCH_LENGTH);
    }

    static String normalizeSavedSearchValue(String value) {
        return normalize(value, MAX_SAVED_SEARCH_LENGTH);
    }

    private static String normalize(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
