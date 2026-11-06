package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WiredChainBudgetTest {

    @Test
    void admitsStacksUntilTheStackCapThenRefusesTheRestOfTheChain() {
        WiredChainBudget budget = new WiredChainBudget(3, 1_000);

        assertTrue(budget.tryAdmit(10));
        assertTrue(budget.tryAdmit(10));
        assertTrue(budget.tryAdmit(10));
        assertFalse(budget.tryAdmit(0));
        assertFalse(budget.tryAdmit(0), "stays exhausted");
        assertEquals(3, budget.stacks());
        assertEquals(30, budget.targets());
    }

    @Test
    void countsSelectedFurniAndUsersAcrossTheChain() {
        WiredChainBudget budget = new WiredChainBudget(100, 50);

        assertTrue(budget.tryAdmit(40));
        assertFalse(budget.tryAdmit(11), "51 targets is over the cap");
        assertFalse(budget.tryAdmit(1), "a refused chain stays refused");
        assertTrue(budget.isExhausted());
        assertEquals(40, budget.targets());
    }

    @Test
    void reportsAnExhaustedChainOnce() {
        WiredChainBudget budget = new WiredChainBudget(1, 10);

        assertFalse(budget.shouldReport(), "nothing to report yet");
        assertTrue(budget.tryAdmit(1));
        assertFalse(budget.tryAdmit(1));
        assertTrue(budget.shouldReport());
        assertFalse(budget.shouldReport());
    }
}
