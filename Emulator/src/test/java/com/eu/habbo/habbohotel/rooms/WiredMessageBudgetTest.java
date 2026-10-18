package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A room shows at most ten wired messages in any five seconds. */
class WiredMessageBudgetTest {

    @Test
    void tenMessagesThenNothingUntilFiveSecondsAfterTheFirst() {
        WiredMessageBudget budget = new WiredMessageBudget();

        for (int i = 0; i < 10; i++) {
            assertTrue(budget.tryAdmit(1000));
        }
        assertFalse(budget.tryAdmit(2500));
        assertFalse(budget.tryAdmit(4000));

        for (int i = 0; i < 10; i++) {
            assertTrue(budget.tryAdmit(8000));
        }
        assertFalse(budget.tryAdmit(12_999));
        assertTrue(budget.tryAdmit(13_000));
    }
}
