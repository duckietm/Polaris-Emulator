package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.habbohotel.rooms.RoomState;

final class RoomSettingsInputGuard {
    static final int MAX_PASSWORD_LENGTH = 64;
    static final int MAX_TAGS = 2;
    static final int MIN_USERS_MAX = 1;
    static final int MAX_USERS_MAX = 200;
    static final int MIN_WALL_OR_FLOOR_SIZE = -2;
    static final int MAX_WALL_OR_FLOOR_SIZE = 1;
    static final int MIN_CHAT_DISTANCE = 1;
    static final int MAX_CHAT_DISTANCE = 99;

    private RoomSettingsInputGuard() {
    }

    static boolean isValidRoomState(int value) {
        return value >= 0 && value < RoomState.values().length;
    }

    static RoomState roomState(int value) {
        RoomState[] states = RoomState.values();
        return states[value];
    }

    static boolean isValidUsersMax(int value) {
        return isInRange(value, MIN_USERS_MAX, MAX_USERS_MAX);
    }

    static boolean isValidTagCount(int value) {
        return isInRange(value, 0, MAX_TAGS);
    }

    static boolean isValidTradeMode(int value) {
        return isInRange(value, 0, 2);
    }

    static boolean isValidModerationOption(int value) {
        return isInRange(value, 0, 2);
    }

    static boolean isValidWallOrFloorSize(int value) {
        return isInRange(value, MIN_WALL_OR_FLOOR_SIZE, MAX_WALL_OR_FLOOR_SIZE);
    }

    static boolean isValidChatMode(int value) {
        return isInRange(value, 0, 2);
    }

    static boolean isValidChatWeight(int value) {
        return isInRange(value, 0, 2);
    }

    static boolean isValidChatSpeed(int value) {
        return isInRange(value, 0, 2);
    }

    static boolean isValidChatDistance(int value) {
        return isInRange(value, MIN_CHAT_DISTANCE, MAX_CHAT_DISTANCE);
    }

    static boolean isValidChatProtection(int value) {
        return isInRange(value, 0, 2);
    }

    static boolean isSafePassword(String password) {
        return password != null && password.length() <= MAX_PASSWORD_LENGTH;
    }

    private static boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }
}
