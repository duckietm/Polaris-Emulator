package com.eu.habbo.messages.incoming.rooms.users;

final class RoomUserInputGuard {
    static final int MIN_ACTION_ID = 0;
    static final int MAX_ACTION_ID = 7;

    private RoomUserInputGuard() {
    }

    static boolean isPositiveId(int id) {
        return id > 0;
    }

    static boolean isValidAction(int action) {
        return action >= MIN_ACTION_ID && action <= MAX_ACTION_ID;
    }
}
