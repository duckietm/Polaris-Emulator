package com.eu.habbo.messages.incoming.housekeeping;

final class HousekeepingInputGuard {
    static final int MAX_LOOKUP_LENGTH = 64;
    static final int MAX_REASON_LENGTH = 500;
    static final int MAX_ALERT_LENGTH = 1000;

    private HousekeepingInputGuard() {
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean isWithinLimit(String value, int maxLength) {
        return value != null && value.length() <= maxLength;
    }

    static String auditValue(String value) {
        String normalized = normalize(value)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');

        return normalized.length() > MAX_REASON_LENGTH ? normalized.substring(0, MAX_REASON_LENGTH) : normalized;
    }
}
