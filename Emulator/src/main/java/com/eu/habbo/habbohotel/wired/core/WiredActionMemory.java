package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;

/**
 * What AIR remembers of an avatar's actions, kept on the unit. Two slots, as AIR keeps them apart:
 *
 * <ul>
 *   <li>the selector's: every action but blow kiss, laugh and dance (those leave the earlier one in place),
 *       read back for 5 s, respect for 2.5 s (Users By Action);
 *   <li>the expression: wave, respect and sleep only, for 2 s (User Performs Action), so a laugh or a sit
 *       does not hide a recent wave.
 * </ul>
 */
public final class WiredActionMemory {
    static final String SELECTOR_ACTION = "wired.remembered_action.id";
    static final String SELECTOR_PARAMETER = "wired.remembered_action.parameter";
    static final String SELECTOR_TIMESTAMP = "wired.remembered_action.timestamp";
    static final String EXPRESSION_ACTION = "wired.recent_expression.id";
    static final String EXPRESSION_TIMESTAMP = "wired.recent_expression.timestamp";

    public static final long SELECTOR_WINDOW_MS = 5_000L;
    public static final long SELECTOR_RESPECT_WINDOW_MS = 2_500L;
    public static final long EXPRESSION_WINDOW_MS = 2_000L;

    private WiredActionMemory() {}

    public static void record(RoomUnit unit, int actionId, int parameter, long now) {
        if (unit == null) {
            return;
        }

        if (actionId != WiredUserActionType.BLOW_KISS
                && actionId != WiredUserActionType.LAUGH
                && actionId != WiredUserActionType.DANCE) {
            unit.getCacheable().put(SELECTOR_ACTION, actionId);
            unit.getCacheable().put(SELECTOR_PARAMETER, parameter);
            unit.getCacheable().put(SELECTOR_TIMESTAMP, now);
        }

        if (actionId == WiredUserActionType.WAVE
                || actionId == WiredUserActionType.RESPECT
                || actionId == WiredUserActionType.RELAX) {
            unit.getCacheable().put(EXPRESSION_ACTION, actionId);
            unit.getCacheable().put(EXPRESSION_TIMESTAMP, now);
        }
    }

    /** The action Users By Action still sees as {action, parameter}, or null once its window is over. */
    public static int[] selectorRecent(RoomUnit unit, long now) {
        if (unit == null
                || !(unit.getCacheable().get(SELECTOR_ACTION) instanceof Integer action)
                || !(unit.getCacheable().get(SELECTOR_TIMESTAMP) instanceof Long timestamp)) {
            return null;
        }

        long window = action == WiredUserActionType.RESPECT ? SELECTOR_RESPECT_WINDOW_MS : SELECTOR_WINDOW_MS;
        long age = now - timestamp;

        if (age < 0 || age > window) {
            return null;
        }

        Object parameter = unit.getCacheable().get(SELECTOR_PARAMETER);
        return new int[] {action, parameter instanceof Integer value ? value : -1};
    }

    /** Whether this wave, respect or sleep happened less than 2 s ago. */
    public static boolean recentExpression(RoomUnit unit, int actionId, long now) {
        if (unit == null
                || !(unit.getCacheable().get(EXPRESSION_ACTION) instanceof Integer action)
                || !(unit.getCacheable().get(EXPRESSION_TIMESTAMP) instanceof Long timestamp)) {
            return false;
        }

        long age = now - timestamp;
        return action == actionId && age >= 0 && age < EXPRESSION_WINDOW_MS;
    }
}
