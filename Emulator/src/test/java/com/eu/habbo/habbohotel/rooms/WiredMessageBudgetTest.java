package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A user is shown at most ten wired messages in any five seconds and forty in any thirty. */
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

    @Test
    void atMostFortyInThirtySecondsEvenWhenSpreadOut() {
        WiredMessageBudget budget = new WiredMessageBudget();

        // 0.6 s apart stays under ten in five seconds.
        for (int i = 0; i < 40; i++) {
            assertTrue(budget.tryAdmit(i * 600L), "message " + i);
        }

        assertFalse(budget.tryAdmit(24_000L));
        assertFalse(budget.tryAdmit(29_999L));
        assertTrue(budget.tryAdmit(30_000L));
    }
}
