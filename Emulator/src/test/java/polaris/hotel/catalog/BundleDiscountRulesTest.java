package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import polaris.messages.outgoing.catalog.DiscountComposer;

/** The bulk discount: the sum the client shows and the purchase charges. */
class BundleDiscountRulesTest {

    /** The sum as CatalogManager had it, and Octane's getCatalogBundleDiscountItemCount still has it. */
    private static int clientFreeItems(int amount, int batch, int perBatch, int bonusMin, int[] thresholds) {
        if (amount <= 0 || batch <= 0) return 0;
        int batches = amount / batch;
        int free = batches * perBatch;
        if (batches >= bonusMin) {
            if (amount % batch == batch - 1) free++;
            free += batches - bonusMin;
        }
        for (int threshold : thresholds) {
            if (amount >= threshold) free++;
        }
        return free;
    }

    @Test
    void chargesWhatTheClientShows() {
        int[][] settings = {{6, 1, 1}, {10, 2, 3}, {3, 1, 0}, {5, 4, 2}};
        int[] thresholds = {40, 99};
        for (int[] s : settings) {
            BundleDiscountRules rules = new BundleDiscountRules(100, s[0], s[1], s[2], thresholds);
            for (int amount = 0; amount <= 100; amount++) {
                int shown = Math.max(0, amount - clientFreeItems(amount, s[0], s[1], s[2], thresholds));
                assertEquals(shown, rules.payableItems(amount), "batch " + s[0] + ", amount " + amount);
            }
        }
    }

    @Test
    void theDefaultRulesGiveAIRsFreeItems() {
        BundleDiscountRules rules = BundleDiscountRules.DEFAULT;
        assertEquals(0, rules.freeItems(5));
        assertEquals(1, rules.freeItems(6));
        assertEquals(2, rules.freeItems(11));
        assertEquals(3, rules.freeItems(12));
        assertEquals(12, rules.freeItems(40));
    }

    @Test
    void bringsSettingsIntoRange() {
        BundleDiscountRules rules = new BundleDiscountRules(0, -3, 5, -1, new int[] {99, 0, 40, 40, 5000});
        assertEquals(1, rules.maxItems());
        assertEquals(0, rules.batchSize());
        assertEquals(0, rules.freePerBatch());
        assertEquals(0, rules.bonusMinimum());
        assertArrayEquals(new int[] {40, 99}, rules.thresholds());
        assertEquals(0, rules.freeItems(50), "no batch size, no discount");

        BundleDiscountRules generous = new BundleDiscountRules(5000, 6, 6, 1, null);
        assertEquals(BundleDiscountRules.MAX_ITEMS, generous.maxItems());
        assertEquals(5, generous.freePerBatch(), "a whole batch is never free");
        assertEquals(new BundleDiscountRules(1000, 6, 5, 1, new int[0]), generous);
    }

    @Test
    void readsTheThresholdSetting() {
        assertArrayEquals(new int[] {40, 99}, BundleDiscountRules.parseThresholds("40;99"));
        assertArrayEquals(new int[] {10, 40, 99}, BundleDiscountRules.parseThresholds(" 99 , 40;x;10;; "));
        assertArrayEquals(new int[0], BundleDiscountRules.parseThresholds(" "));
        assertThrows(IllegalArgumentException.class, () -> BundleDiscountRules.parseThresholds("invalid"));
    }

    @Test
    void theComposerSendsTheCheckedRules() {
        int batch = DiscountComposer.DISCOUNT_BATCH_SIZE;
        int free = DiscountComposer.DISCOUNT_AMOUNT_PER_BATCH;
        try {
            DiscountComposer.DISCOUNT_BATCH_SIZE = 4;
            DiscountComposer.DISCOUNT_AMOUNT_PER_BATCH = 9;
            assertEquals(3, DiscountComposer.rules().freePerBatch());
        } finally {
            DiscountComposer.DISCOUNT_BATCH_SIZE = batch;
            DiscountComposer.DISCOUNT_AMOUNT_PER_BATCH = free;
        }
    }
}
