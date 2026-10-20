package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_MAX;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_MIN;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_RANDOM;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_RSHIFT;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.applyOperation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Operators whose meaning follows the official client (turbo-cloud 6cb26b45, AS3 ChangeVariable). */
class WiredEffectChangeVariableValueOfficialMathTest {

    @Test
    void setMinimumIsAFloor() {
        assertEquals(10, applyOperation(OP_MIN, 3, 10));
        assertEquals(15, applyOperation(OP_MIN, 15, 10));
    }

    @Test
    void setMaximumIsACeiling() {
        assertEquals(3, applyOperation(OP_MAX, 3, 10));
        assertEquals(10, applyOperation(OP_MAX, 15, 10));
    }

    @Test
    void rightShiftIsLogical() {
        assertEquals(4, applyOperation(OP_RSHIFT, 16, 2));
        assertEquals(Integer.MAX_VALUE, applyOperation(OP_RSHIFT, -1, 1));
    }

    @Test
    void randomWithTheLargestBoundDoesNotOverflow() {
        for (int i = 0; i < 100; i++) {
            int value = applyOperation(OP_RANDOM, 0, Integer.MAX_VALUE);
            assertTrue(value >= 0, "value " + value);
        }
        assertEquals(0, applyOperation(OP_RANDOM, 5, 0));
    }
}
