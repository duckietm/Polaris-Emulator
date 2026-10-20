package com.eu.habbo.habbohotel.wired;

import java.util.function.Supplier;

/**
 * Where a variable change came from, so the "variable changed" trigger can listen to some sources
 * and ignore others. Three origins exist today: a wired box in this room, the web API, and the
 * creator tools (inspection, management, the inline editors). The origin travels with the thread
 * that performs the write: the few entry points that are not the wired engine label themselves,
 * and everything else is a wired write.
 *
 * <p>A trigger stores the origins it listens to as a bit mask over these codes; {@link #MASK_ALL}
 * (and, for boxes saved before the mask existed, 0) means every origin.
 */
public final class WiredVariableChangeOrigin {

    public static final int WIRED = 0;
    public static final int WEB_API = 1;
    public static final int CREATOR_TOOLS = 2;
    public static final int OTHER_ROOM = 3;
    public static final int MASK_ALL = -1;

    private static final int HIGHEST = OTHER_ROOM;
    private static final int MASK_BITS = (1 << (HIGHEST + 1)) - 1;
    private static final ThreadLocal<Integer> CURRENT = ThreadLocal.withInitial(() -> WIRED);

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private WiredVariableChangeOrigin() {}

    public static int current() {
        return CURRENT.get();
    }

    public static void run(int origin, Runnable body) {
        int previous = enter(origin);
        try {
            body.run();
        } finally {
            exit(previous);
        }
    }

    public static void runChecked(int origin, ThrowingRunnable body) throws Exception {
        int previous = enter(origin);
        try {
            body.run();
        } finally {
            exit(previous);
        }
    }

    public static <T> T call(int origin, Supplier<T> body) {
        int previous = enter(origin);
        try {
            return body.get();
        } finally {
            exit(previous);
        }
    }

    /** Whether a trigger with this mask listens to a change of this origin. */
    public static boolean accepts(int mask, int origin) {
        if (mask == MASK_ALL || mask == 0) return true;
        return (mask & (1 << normalize(origin))) != 0;
    }

    public static int normalize(int origin) {
        return (origin < WIRED || origin > HIGHEST) ? WIRED : origin;
    }

    /** Keeps only the bits that name an origin; anything negative means every origin. */
    public static int normalizeMask(int mask) {
        return (mask < 0) ? MASK_ALL : (mask & MASK_BITS);
    }

    /** Sets the origin for the calling thread and answers the previous one; pair with {@link #exit}. */
    public static int enter(int origin) {
        int previous = CURRENT.get();
        CURRENT.set(normalize(origin));
        return previous;
    }

    public static void exit(int previous) {
        if (previous == WIRED) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
