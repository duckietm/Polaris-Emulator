package com.eu.habbo.messages.incoming.friends;

final class FriendInputGuard {
    static final int MAX_USERNAME_LENGTH = 15;
    static final int MAX_MESSAGE_LENGTH = 255;
    static final int MAX_RELATION_ID = 3;

    private FriendInputGuard() {
    }

    static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    static boolean isValidUsername(String username) {
        return username != null && !username.isBlank() && username.length() <= MAX_USERNAME_LENGTH;
    }

    static String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }

        String normalized = message.trim();
        return normalized.length() > MAX_MESSAGE_LENGTH ? normalized.substring(0, MAX_MESSAGE_LENGTH) : normalized;
    }

    static boolean isValidRelation(int relationId) {
        return relationId >= 0 && relationId <= MAX_RELATION_ID;
    }
}
