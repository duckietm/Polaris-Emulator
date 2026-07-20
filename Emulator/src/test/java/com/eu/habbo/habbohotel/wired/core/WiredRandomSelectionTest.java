package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WiredRandomSelectionTest {

    @Test
    void selectsOnlyIndexesInsideTheRequestedBound() {
        for (int iteration = 0; iteration < 10_000; iteration++) {
            int selected = WiredEngine.selectRandomIndex(7);

            assertTrue(selected >= 0 && selected < 7);
        }
    }

    @Test
    void preservesSingleChoiceAndInvalidBoundBehavior() {
        assertEquals(0, WiredEngine.selectRandomIndex(1));
        assertThrows(IllegalArgumentException.class, () -> WiredEngine.selectRandomIndex(0));
    }
}
