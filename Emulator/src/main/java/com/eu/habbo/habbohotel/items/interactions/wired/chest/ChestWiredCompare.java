package com.eu.habbo.habbohotel.items.interactions.wired.chest;

/**
 * Comparison operators for wired chest conditions. Values match the Nitro client
 * {@code WiredComparisonOperator} encoding and official Habbo {@code ChestHasAmount}.
 */
public final class ChestWiredCompare {
    public static final int LESS = 0;
    public static final int EQUAL = 1;
    public static final int GREATER = 2;
    public static final int LESS_EQUAL = 3;
    public static final int NOT_EQUAL = 4;
    public static final int GREATER_EQUAL = 5;

    private ChestWiredCompare() {
    }

    public static boolean compare(int actual, int expected, int operator) {
        switch (operator) {
            case LESS:
                return actual < expected;
            case EQUAL:
                return actual == expected;
            case GREATER:
                return actual > expected;
            case LESS_EQUAL:
                return actual <= expected;
            case NOT_EQUAL:
                return actual != expected;
            case GREATER_EQUAL:
            default:
                return actual >= expected;
        }
    }

    public static int normalize(int operator) {
        if (operator >= LESS && operator <= GREATER_EQUAL) {
            return operator;
        }
        return GREATER_EQUAL;
    }
}
