package com.eu.habbo.habbohotel.wired.arrays;

import java.util.concurrent.ThreadLocalRandom;

/** Numeric operations shared with WIRED Effect: Change Variable Value. */
public enum WiredArrayNumericOperation {
    ASSIGN(0),
    ADD(1),
    SUBTRACT(2),
    MULTIPLY(3),
    DIVIDE(4),
    POWER(5),
    MODULO(6),
    MIN(40),
    MAX(41),
    RANDOM_UPPER_BOUND(50),
    ABSOLUTE(60),
    BITWISE_AND(100),
    BITWISE_OR(101),
    BITWISE_XOR(102),
    BITWISE_NOT(103),
    LEFT_SHIFT(104),
    RIGHT_SHIFT(105),
    BIT_COUNT(110),
    NEXT_LOW_BIT(111),
    NEXT_HIGH_BIT(112),
    PREVIOUS_LOW_BIT(113),
    PREVIOUS_HIGH_BIT(114),
    GET_BIT(115),
    SET_BIT(116),
    CLEAR_BIT(117),
    TOGGLE_BIT(118),
    NEXT_LOW_BIT_EXCLUSIVE(119),
    NEXT_HIGH_BIT_EXCLUSIVE(120),
    PREVIOUS_LOW_BIT_EXCLUSIVE(121),
    PREVIOUS_HIGH_BIT_EXCLUSIVE(122);

    private final int code;

    WiredArrayNumericOperation(int code) {
        this.code = code;
    }

    public int code() {
        return this.code;
    }

    public boolean isUnary() {
        return this == ABSOLUTE || this == BITWISE_NOT || this == BIT_COUNT;
    }

    public long apply(long current, long reference) {
        return switch (this) {
            case ASSIGN -> reference;
            case ADD -> current + reference;
            case SUBTRACT -> current - reference;
            case MULTIPLY -> current * reference;
            case DIVIDE -> reference == 0L ? current : current / reference;
            case POWER -> (long) Math.pow(current, reference);
            case MODULO -> reference == 0L ? current : current % reference;
            case MIN -> Math.min(current, reference);
            case MAX -> Math.max(current, reference);
            case RANDOM_UPPER_BOUND ->
                reference <= 0L
                        ? 0L
                        : ThreadLocalRandom.current()
                                .nextLong(reference == Long.MAX_VALUE ? Long.MAX_VALUE : reference + 1L);
            case ABSOLUTE -> Math.abs(current);
            case BITWISE_AND -> current & reference;
            case BITWISE_OR -> current | reference;
            case BITWISE_XOR -> current ^ reference;
            case BITWISE_NOT -> ~current;
            case LEFT_SHIFT -> current << reference;
            case RIGHT_SHIFT -> current >> reference;
            case BIT_COUNT -> Long.bitCount(current);
            case GET_BIT -> isBitPosition(reference) ? (current >>> reference) & 1L : 0L;
            case SET_BIT -> isBitPosition(reference) ? current | (1L << reference) : current;
            case CLEAR_BIT -> isBitPosition(reference) ? current & ~(1L << reference) : current;
            case TOGGLE_BIT -> isBitPosition(reference) ? current ^ (1L << reference) : current;
            case NEXT_LOW_BIT -> scanBit(current, reference, 1, false);
            case NEXT_HIGH_BIT -> scanBit(current, reference, 1, true);
            case PREVIOUS_LOW_BIT -> scanBit(current, reference, -1, false);
            case PREVIOUS_HIGH_BIT -> scanBit(current, reference, -1, true);
            case NEXT_LOW_BIT_EXCLUSIVE -> isBitPosition(reference) ? scanBit(current, reference + 1, 1, false) : -1;
            case NEXT_HIGH_BIT_EXCLUSIVE -> isBitPosition(reference) ? scanBit(current, reference + 1, 1, true) : -1;
            case PREVIOUS_LOW_BIT_EXCLUSIVE ->
                isBitPosition(reference) ? scanBit(current, reference - 1, -1, false) : -1;
            case PREVIOUS_HIGH_BIT_EXCLUSIVE ->
                isBitPosition(reference) ? scanBit(current, reference - 1, -1, true) : -1;
        };
    }

    private static boolean isBitPosition(long position) {
        return position >= 0 && position < Long.SIZE;
    }

    private static long scanBit(long value, long from, int step, boolean wantSet) {
        for (long position = from; isBitPosition(position); position += step) {
            if ((((value >>> position) & 1L) == 1L) == wantSet) return position;
        }
        return -1L;
    }

    public static WiredArrayNumericOperation fromCode(int code) {
        for (WiredArrayNumericOperation operation : values()) {
            if (operation.code == code) return operation;
        }
        return null;
    }
}
