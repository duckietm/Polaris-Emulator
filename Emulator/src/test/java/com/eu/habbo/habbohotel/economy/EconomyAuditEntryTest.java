package com.eu.habbo.habbohotel.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomyAuditEntryTest {

    @Test
    void redemptionBuildsStableOperationIdentityAndBalances() {
        EconomyAuditEntry entry = EconomyAuditEntry.redemption(42, 9001, -1, 50, 100, 150, "CF_50_goldbar");

        assertEquals("furniture-redeem:9001", entry.operationId());
        assertEquals(42, entry.userId());
        assertEquals(42, entry.actorId());
        assertEquals("furniture_redeem", entry.operation());
        assertEquals("furniture.redeem", entry.reason());
        assertEquals(-1, entry.currencyType());
        assertEquals(50, entry.amount());
        assertEquals(100, entry.balanceBefore());
        assertEquals(150, entry.balanceAfter());
        assertEquals(9001, entry.itemId());
        assertEquals("CF_50_goldbar", entry.context());
    }

    @Test
    void redemptionRejectsInvalidEconomicValues() {
        assertThrows(IllegalArgumentException.class,
                () -> EconomyAuditEntry.redemption(42, 9001, -1, 0, 100, 100, "CF_0_invalid"));
        assertThrows(IllegalArgumentException.class,
                () -> EconomyAuditEntry.redemption(0, 9001, -1, 50, 100, 150, "CF_50_goldbar"));
    }
}
