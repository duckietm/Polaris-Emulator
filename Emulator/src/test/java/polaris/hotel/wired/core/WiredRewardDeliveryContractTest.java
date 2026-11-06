package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A wired reward was counted as given even when the payout or the catalog purchase was refused. */
class WiredRewardDeliveryContractTest {

    private static final Path SOURCE = Path.of("src/main/java/polaris/hotel/wired/core/WiredManager.java");

    @Test
    void aRewardCountsOnlyWhenItWasDelivered() throws Exception {
        String source = Files.readString(SOURCE);

        assertFalse(source.contains("user.giveCredits(amount);\n            completeReward("));
        assertFalse(source.contains("purchaseItem(null, item, user, 1, \"\", true);\n            completeReward("));
        assertTrue(source.contains("if (!grantRewardCurrency(user, -1, amount)) return false;"));
        assertTrue(source.contains("if (!grantRewardCurrency(user, type, points)) return false;"));
        assertTrue(source.contains("purchaseItemDelivered(null, item, user, 1, \"\", true)"));
        assertTrue(source.contains("user.applyCredits(amount, \"wired.reward\", operationId, userId)"));
    }
}
