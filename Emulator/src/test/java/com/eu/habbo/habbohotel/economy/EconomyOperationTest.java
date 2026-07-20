package com.eu.habbo.habbohotel.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class EconomyOperationTest {
    @Test
    void operationCarriesStableIdentityReasonActorAndSignedDelta() {
        EconomyOperation operation = new EconomyOperation(
                "marketplace:offer:77:buyer",
                42,
                9,
                "marketplace_purchase",
                "catalog.marketplace.buy",
                -1,
                -250,
                8001,
                "{\"offerId\":77}");

        assertEquals("marketplace:offer:77:buyer", operation.operationId());
        assertEquals(9, operation.actorId());
        assertEquals("catalog.marketplace.buy", operation.reason());
        assertEquals(-250, operation.delta());
    }

    @Test
    void rejectsAmbiguousOrUnsafeOperations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EconomyOperation("", 42, null, "grant", "admin.grant", -1, 5, null, ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EconomyOperation("grant:1", 0, null, "grant", "admin.grant", -1, 5, null, ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EconomyOperation("grant:1", 42, null, "grant", "", -1, 5, null, ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EconomyOperation("grant:1", 42, null, "grant", "admin.grant", -1, 0, null, ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EconomyOperation("grant:1", 42, null, "grant", "admin.grant", -2, 5, null, ""));
    }

    @Test
    void checkedBalanceRejectsOverdraftAndOverflow() {
        assertEquals(125, EconomyLedger.checkedBalance(100, 25));
        assertThrows(IllegalArgumentException.class, () -> EconomyLedger.checkedBalance(10, -11));
        assertThrows(IllegalArgumentException.class, () -> EconomyLedger.checkedBalance(Integer.MAX_VALUE, 1));
    }

    @Test
    void walletBatchCannotSpanUsersWithAnAmbiguousLockOrder() {
        EconomyOperation first =
                new EconomyOperation("batch:user:1", 1, 1, "test", "test.batch", EconomyLedger.CREDITS, 1, null, "");
        EconomyOperation second =
                new EconomyOperation("batch:user:2", 2, 2, "test", "test.batch", EconomyLedger.CREDITS, 1, null, "");

        assertThrows(IllegalArgumentException.class, () -> EconomyLedger.executeBatch(List.of(first, second)));
    }
}
