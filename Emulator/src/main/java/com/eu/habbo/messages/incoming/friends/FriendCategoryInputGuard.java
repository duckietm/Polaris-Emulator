package com.eu.habbo.messages.incoming.friends;

final class FriendCategoryInputGuard {
    static final int MAX_NAME_LENGTH = 25;
    static final int MAX_CATEGORIES = 10;

    private FriendCategoryInputGuard() {}

    static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    static boolean isValidName(String name) {
        return !name.isEmpty() && name.length() <= MAX_NAME_LENGTH;
    }
}
