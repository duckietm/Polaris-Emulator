package com.eu.habbo.messages.incoming.rooms.users;

final class RoomUserInputGuard {
    static final int MIN_ACTION_ID = 0;
    static final int MAX_ACTION_ID = 7;
    static final int SIX_SEVEN_ACTION_ID = 67;

    private RoomUserInputGuard() {}

    static boolean isPositiveId(int id) {
        return id > 0;
    }

    static boolean isValidAction(int action) {
        return (action >= MIN_ACTION_ID && action <= MAX_ACTION_ID) || action == SIX_SEVEN_ACTION_ID;
    }

    /**
     * Expressions only Habbo Club members may use: blow a kiss, laugh, 4, 6 and 67. Observed on AIR
     * WIN63-202610201032: from a user without club they reach nobody.
     */
    static boolean isClubOnlyAction(int action) {
        return action == 2 || action == 3 || action == 4 || action == 6 || action == SIX_SEVEN_ACTION_ID;
    }

    /** Dances 2 to 4 need Habbo Club; without it the request is ignored and the current dance stays (AIR). */
    static boolean isClubOnlyDance(int danceId) {
        return danceId > 1 && danceId <= 4;
    }
}
