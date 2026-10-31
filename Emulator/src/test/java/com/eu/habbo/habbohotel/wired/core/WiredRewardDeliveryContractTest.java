package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A wired reward was counted as given even when the payout or the catalog purchase was refused. */
class WiredRewardDeliveryContractTest {

    private static final Path SOURCE = Path.of("src/main/java/com/eu/habbo/habbohotel/wired/core/WiredManager.java");

    @Test
    void aRewardCountsOnlyWhenItWasDelivered() throws Exception {
        String source = Files.readString(SOURCE);

        assertFalse(source.contains("habbo.giveCredits(amount);\n            completeReward("));
        assertFalse(source.contains("purchaseItem(null, item, habbo, 1, \"\", true);\n            completeReward("));
        assertTrue(source.contains("if (!grantRewardCurrency(habbo, -1, amount)) return false;"));
        assertTrue(source.contains("if (!grantRewardCurrency(habbo, type, points)) return false;"));
        assertTrue(source.contains("purchaseItemDelivered(null, item, habbo, 1, \"\", true)"));
        assertTrue(source.contains("habbo.applyCredits(amount, \"wired.reward\", operationId, userId)"));
    }
}
