package polaris.hotel.catalog;

import java.util.concurrent.ConcurrentHashMap;
import polaris.Emulator;

/**
 * What a Furni-Matic exchange costs and how long a user waits between two, as the official client expects them
 * ("recycler.ducket_cost", "recycler.timeout_seconds"). Both are off (0) unless the hotel sets them, so a hotel that
 * never configured them keeps its free exchange without a wait.
 */
public final class RecyclerRules {
    /** Activity point type of Duckets. */
    public static final int DUCKETS = 0;

    public static final String DUCKET_COST_KEY = "recycler.ducket_cost";
    public static final String TIMEOUT_KEY = "recycler.timeout_seconds";

    /** A wait is never longer than a day, whatever the setting says. */
    static final int MAX_TIMEOUT_SECONDS = 24 * 60 * 60;

    /** User id to the second the next exchange is allowed; entries leave when they are read after that second. */
    private static final ConcurrentHashMap<Integer, Integer> NEXT_ALLOWED = new ConcurrentHashMap<>();

    private RecyclerRules() {}

    public static int ducketCost() {
        return clampCost(Emulator.getConfig().getInt(DUCKET_COST_KEY, 0));
    }

    public static int timeoutSeconds() {
        return clampTimeout(Emulator.getConfig().getInt(TIMEOUT_KEY, 0));
    }

    static int clampCost(int configured) {
        return Math.max(0, configured);
    }

    static int clampTimeout(int configured) {
        return Math.max(0, Math.min(configured, MAX_TIMEOUT_SECONDS));
    }

    /** Seconds the user still has to wait, 0 when an exchange is allowed. */
    public static int secondsToWait(int userId) {
        return secondsToWait(userId, Emulator.getIntUnixTimestamp());
    }

    static int secondsToWait(int userId, int now) {
        Integer next = NEXT_ALLOWED.get(userId);
        if (next == null) return 0;

        if (next <= now) {
            NEXT_ALLOWED.remove(userId, next);
            return 0;
        }

        return next - now;
    }

    /** Starts the wait after a finished exchange. */
    public static void exchanged(int userId) {
        exchanged(userId, Emulator.getIntUnixTimestamp(), timeoutSeconds());
    }

    static void exchanged(int userId, int now, int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            NEXT_ALLOWED.remove(userId);
            return;
        }

        NEXT_ALLOWED.put(userId, now + timeoutSeconds);
    }
}
