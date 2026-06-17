package com.eu.habbo.messages.incoming.modtool;

final class ModToolInputGuard {
    static final int MAX_MESSAGE_LENGTH = 1000;

    private ModToolInputGuard() {
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean isSafeMessage(String value) {
        return value != null && !value.isEmpty() && value.length() <= MAX_MESSAGE_LENGTH;
    }
}
