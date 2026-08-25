package com.eu.habbo.habbohotel.items.interactions.wired;

/**
 * Comparison operators for wired chest conditions.
 *
 * <p>The integer encoding is the WIRE CONTRACT shared with the client
 * ({@code ui/src/components/wired/views/WiredComparisonOperator.tsx}) and the
 * official deobfuscated {@code ChestHasAmount}. It must be mapped by VALUE, never
 * by ordinal:
 *
 * <pre>0 = &lt;    1 = =    2 = &gt;    3 = &lt;=    4 = !=    5 = &gt;=</pre>
 *
 * The default ({@code >=}) preserves the chest conditions' historical hard-coded
 * behaviour for saves written before the operator existed.
 */
public final class WiredComparison {
    public static final int LESS = 0;
    public static final int EQUAL = 1;
    public static final int GREATER = 2;
    public static final int LESS_EQUAL = 3;
    public static final int NOT_EQUAL = 4;
    public static final int GREATER_EQUAL = 5;

    private WiredComparison() {
    }

    public static int normalize(int value) {
        return (value >= LESS && value <= GREATER_EQUAL) ? value : GREATER_EQUAL;
    }

    public static boolean compare(int left, int right, int operator) {
        switch (operator) {
            case LESS:
                return left < right;
            case EQUAL:
                return left == right;
            case GREATER:
                return left > right;
            case LESS_EQUAL:
                return left <= right;
            case NOT_EQUAL:
                return left != right;
            case GREATER_EQUAL:
            default:
                return left >= right;
        }
    }
}
