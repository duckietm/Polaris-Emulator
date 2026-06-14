package com.eu.habbo.messages.incoming.housekeeping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HousekeepingMutationGuardTest {
    @Test
    void positiveGrantAmountsMustBeStrictlyPositiveAndBounded() {
        assertFalse(HousekeepingMutationGuard.isPositiveGrantAmount(-1));
        assertFalse(HousekeepingMutationGuard.isPositiveGrantAmount(0));
        assertTrue(HousekeepingMutationGuard.isPositiveGrantAmount(1));
        assertTrue(HousekeepingMutationGuard.isPositiveGrantAmount(HousekeepingMutationGuard.MAX_GRANT));
        assertFalse(HousekeepingMutationGuard.isPositiveGrantAmount(HousekeepingMutationGuard.MAX_GRANT + 1));
    }

    @Test
    void currencyTypesCannotBeNegative() {
        assertFalse(HousekeepingMutationGuard.isCurrencyType(-1));
        assertTrue(HousekeepingMutationGuard.isCurrencyType(0));
        assertTrue(HousekeepingMutationGuard.isCurrencyType(101));
    }
}
