package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The budget flood mode: two lines in hand, one more on every 600 ms step, extra lines dropped. */
class ChatFloodBudgetTest {
    private static final long START = 1_800_000_000_000L;

    @Test
    void twoLinesInHandAndOneMoreOnEveryStep() {
        ChatFloodBudget budget = new ChatFloodBudget();

        assertTrue(budget.tryTake(START));
        assertTrue(budget.tryTake(START + 100));
        assertFalse(budget.tryTake(START + 200));
        assertTrue(budget.tryTake(START + 600));
        assertFalse(budget.tryTake(START + 700));
        assertTrue(budget.tryTake(START + 1800));
        assertTrue(budget.tryTake(START + 1850));
        assertFalse(budget.tryTake(START + 1900));
    }

    @Test
    void linesSixHundredMillisecondsApartAllGoOut() {
        ChatFloodBudget budget = new ChatFloodBudget();
        for (int i = 0; i < 20; i++) {
            assertTrue(budget.tryTake(START + i * 600L));
        }
    }

    @Test
    void linesFourHundredMillisecondsApartDropEveryThirdOnceTheBurstIsSpent() {
        ChatFloodBudget budget = new ChatFloodBudget();
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            pattern.append(budget.tryTake(START + i * 400L) ? 'o' : 'x');
        }
        assertEquals("ooooxooxooxo", pattern.toString());
    }

    @Test
    void theMuteModeIsTheDefault() {
        assertFalse(ChatFloodBudget.isBudgetMode());
    }
}
