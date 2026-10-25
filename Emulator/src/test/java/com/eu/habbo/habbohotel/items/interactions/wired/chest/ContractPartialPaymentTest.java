package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A contract's payment could be taken in part - a chest or the database refusing some furni, or the
 * credit furni failing to redeem - and the reward was handed out anyway, while what was not taken went
 * back to the player when the table closed. Now what reached a chest comes back and nothing is given.
 */
class ContractPartialPaymentTest {

    private static ChestFurniStoredItem row(int inventoryId, int baseItemId) {
        ChestFurniStoredItem row = new ChestFurniStoredItem();
        row.inventoryId = inventoryId;
        row.baseItemId = baseItemId;
        return row;
    }

    @Test
    void depositedRowsCanBeTakenBackByTheirIds() {
        ChestStorage storage = new ChestStorage();
        storage.addFurniItem(row(101, 7));
        storage.addFurniItem(row(102, 7));
        storage.addFurniItem(row(103, 8));

        List<ChestFurniStoredItem> removed = storage.removeFurniByInventoryIds(Set.of(101, 103));

        assertEquals(2, removed.size());
        assertEquals(
                List.of(102),
                storage.furniItems().stream().map(r -> r.inventoryId).toList());
        assertEquals(1, storage.furniItemCount());
    }

    @Test
    void aPartialPaymentIsRefundedBeforeAnyRewardAndCreditsGoLast() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/items/interactions/wired/chest/WiredTradingManager.java"));

        int check = source.indexOf("payment.consumed().size() != furniPayment.size() + creditPayment.size()");
        int refund = source.indexOf("this.refundDeposits(habbo, payment);", check);
        int give = source.indexOf("WiredChestPayouts.give(habbo, this.room, chests, reward", check);

        assertTrue(
                check > 0 && refund > check && give > refund, "the payment is checked and refunded before the reward");
        assertTrue(
                source.contains("if (coins > 0 && remaining.isEmpty()) {"),
                "credits are redeemed only once all furni went in");
    }
}
