package com.eu.habbo.habbohotel.rooms;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Inside a Give Variable, every value the box writes counts as created, also an override onto a value the holder
 * already had, as on the official server; a give that leaves the value as it was fires nothing.
 */
public final class WiredVariableGiveScope {
    private static final ThreadLocal<Boolean> GIVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private WiredVariableGiveScope() {}

    /** Runs {@code give} with its variable events reported as created. */
    public static boolean give(BooleanSupplier give) {
        boolean previous = GIVING.get();
        GIVING.set(Boolean.TRUE);
        try {
            return give.getAsBoolean();
        } finally {
            GIVING.set(previous);
        }
    }

    public static boolean active() {
        return GIVING.get();
    }

    /**
     * Whether a variable event should be left out: inside a give, a holder that kept the value it had fires nothing.
     */
    static boolean suppresses(boolean existedBefore, Integer previousValue, boolean existsAfter, Integer currentValue) {
        return active() && existedBefore && existsAfter && Objects.equals(previousValue, currentValue);
    }

    /** The created flag of a variable event, which a give always sets while the holder has the variable. */
    static boolean created(boolean existedBefore, boolean existsAfter) {
        return existsAfter && (!existedBefore || active());
    }
}
