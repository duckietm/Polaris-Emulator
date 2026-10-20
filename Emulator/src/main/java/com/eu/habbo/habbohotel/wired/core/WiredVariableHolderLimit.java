package com.eu.habbo.habbohotel.wired.core;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * How many different variables one furni or one user may hold in a room ({@code wired.variables.holder.max}). Habbo
 * has such a cap: past it a variable is not given and the monitor counts TOO_MANY_VARIABLES (error 6, "you have given
 * too many different Wired variables to a user or a furni. However, this limit is very high", {@code
 * wiredmenu.error_info.6}; {@code room.error.wired.too_many_variables}). Habbo does not publish the number, so the
 * default is a hotel choice well above the permanent user cap (93) a room can fill.
 *
 * <p>Only a variable new to the holder is refused; changing a value it already holds always goes through, and values
 * restored from the database are never dropped. A cap of 0 or less turns the check off.
 */
public final class WiredVariableHolderLimit {

    public static final int DEFAULT_MAX_VARIABLES_PER_HOLDER = 200;

    private static final AtomicInteger MAX_VARIABLES_PER_HOLDER = new AtomicInteger(DEFAULT_MAX_VARIABLES_PER_HOLDER);

    private WiredVariableHolderLimit() {}

    public static int maxVariablesPerHolder() {
        return MAX_VARIABLES_PER_HOLDER.get();
    }

    /** Sets the cap; 0 or less turns it off. */
    public static void configure(int maxVariablesPerHolder) {
        MAX_VARIABLES_PER_HOLDER.set(maxVariablesPerHolder);
    }

    /** Whether a holder that holds {@code heldVariables} variables may be given one more. */
    public static boolean hasRoomFor(int heldVariables) {
        int max = MAX_VARIABLES_PER_HOLDER.get();
        return max <= 0 || heldVariables < max;
    }

    /**
     * Counts a refused variable in the room's diagnostics (TOO_MANY_VARIABLES).
     *
     * @param holderKind "furni" or "user", for the reason line
     */
    public static void noteRefused(int roomId, String holderKind, int holderId, String variableName, int definitionId) {
        WiredManager.noteTooManyVariables(
                roomId,
                "The " + holderKind + " #" + holderId + " already holds " + MAX_VARIABLES_PER_HOLDER.get()
                        + " variables; "
                        + (variableName == null || variableName.isEmpty() ? "a variable" : variableName)
                        + " was not given",
                variableName == null ? "" : variableName,
                definitionId);
    }
}
