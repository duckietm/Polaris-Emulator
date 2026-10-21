package com.eu.habbo.habbohotel.rooms.raidprotection;

/**
 * How suspicious a visitor is to a room's raid protection. Worked out from what the hotel already
 * holds in memory (rights, staff permission, friendship with the owner, the account's age and the
 * visitor's room history), so a check costs no database work.
 */
public final class RaidSuspicion {
    /** The owner, someone with rights, staff, or a friend of the owner: never counted or acted on. */
    public static final int TRUSTED = -1;
    /** A returning visitor with an established account. */
    public static final int KNOWN = 0;
    /** A first visit, or an account younger than the hotel's new-account age. */
    public static final int UNKNOWN = 1;
    /** An account younger than a day. */
    public static final int NEW_ACCOUNT = 2;

    static final long DAY_SECONDS = 86_400L;

    /** The lowest level the guard keeps out, by guard sensitivity (low, medium, high). */
    private static final int[] GUARD_MIN_LEVEL = {NEW_ACCOUNT, UNKNOWN, KNOWN};

    private RaidSuspicion() {}

    public static int level(boolean trusted, boolean firstVisit, long accountAgeSeconds, int newAccountDays) {
        if (trusted) return TRUSTED;
        if (accountAgeSeconds < DAY_SECONDS) return NEW_ACCOUNT;
        if (firstVisit || accountAgeSeconds < Math.max(1, newAccountDays) * DAY_SECONDS) return UNKNOWN;
        return KNOWN;
    }

    /** Whether an entry or a chat line of this visitor counts towards detecting a raid. */
    public static boolean counts(int level) {
        return level >= UNKNOWN;
    }

    /** Whether the guard keeps this visitor out during a raid at the given guard sensitivity. */
    public static boolean guardBlocks(int level, int guardSensitivity) {
        if (level <= TRUSTED) return false;
        int index = Math.max(0, Math.min(GUARD_MIN_LEVEL.length - 1, guardSensitivity));
        return level >= GUARD_MIN_LEVEL[index];
    }
}
