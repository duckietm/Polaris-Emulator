package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_ADD;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_ASSIGN;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_DIV;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_MOD;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_MUL;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_POW;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_SUB;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.applyOperation;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The arithmetic family of Change Variable Value. A value is a 32-bit integer: the operators that can leave the range
 * stop at its ends instead of wrapping, and a division by zero leaves the value as it was.
 */
class WiredEffectChangeVariableValueArithmeticTest {

    @Test
    void additionAndSubtractionStopAtTheEndsOfTheRange() {
        assertEquals(7, applyOperation(OP_ADD, 3, 4));
        assertEquals(Integer.MAX_VALUE, applyOperation(OP_ADD, Integer.MAX_VALUE, 1));
        assertEquals(Integer.MIN_VALUE, applyOperation(OP_ADD, Integer.MIN_VALUE, -1));
        assertEquals(-1, applyOperation(OP_SUB, 3, 4));
        assertEquals(Integer.MIN_VALUE, applyOperation(OP_SUB, Integer.MIN_VALUE, 1));
        assertEquals(Integer.MAX_VALUE, applyOperation(OP_SUB, Integer.MAX_VALUE, -1));
    }

    @Test
    void multiplicationStopsAtTheEndsOfTheRange() {
        assertEquals(12, applyOperation(OP_MUL, 3, 4));
        assertEquals(Integer.MAX_VALUE, applyOperation(OP_MUL, 100_000, 100_000));
        assertEquals(Integer.MIN_VALUE, applyOperation(OP_MUL, 100_000, -100_000));
        assertEquals(0, applyOperation(OP_MUL, Integer.MAX_VALUE, 0));
    }

    @Test
    void divisionTruncatesTowardZeroAndLeavesTheValueOnAZeroDivisor() {
        assertEquals(3, applyOperation(OP_DIV, 7, 2));
        assertEquals(-3, applyOperation(OP_DIV, -7, 2));
        assertEquals(7, applyOperation(OP_DIV, 7, 0));
        assertEquals(Integer.MIN_VALUE, applyOperation(OP_DIV, Integer.MIN_VALUE, -1));
    }

    @Test
    void theRemainderTakesTheSignOfTheValueAndLeavesItOnAZeroDivisor() {
        assertEquals(1, applyOperation(OP_MOD, 7, 3));
        assertEquals(-1, applyOperation(OP_MOD, -7, 3));
        assertEquals(7, applyOperation(OP_MOD, 7, 0));
        assertEquals(0, applyOperation(OP_MOD, Integer.MIN_VALUE, -1));
    }

    @Test
    void aPowerIsExactInsideTheRangeAndStopsAtItsEnds() {
        assertEquals(1024, applyOperation(OP_POW, 2, 10));
        assertEquals(-8, applyOperation(OP_POW, -2, 3));
        assertEquals(1, applyOperation(OP_POW, 0, 0));
        assertEquals(Integer.MAX_VALUE, applyOperation(OP_POW, 2, 40));
        assertEquals(Integer.MIN_VALUE, applyOperation(OP_POW, -2, 41));
        assertEquals(0, applyOperation(OP_POW, 2, -1), "a negative exponent gives zero, not a fraction");
        assertEquals(1, applyOperation(OP_POW, 5, 0));
    }

    @Test
    void assigningWithoutAnOperandKeepsTheValue() {
        assertEquals(9, applyOperation(OP_ASSIGN, 9, null));
        assertEquals(5, applyOperation(OP_ASSIGN, 9, 5));
    }
}
