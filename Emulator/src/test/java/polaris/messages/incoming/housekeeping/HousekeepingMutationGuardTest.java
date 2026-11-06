package polaris.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
