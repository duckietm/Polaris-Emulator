package polaris.hotel.catalog;

import java.util.Arrays;
import java.util.TreeSet;

/**
 * The catalogue's bulk discount: buying {@code batchSize} of an offer gives {@code freePerBatch} of them free, with
 * AIR's bonus and extra thresholds on top. The same rules go to the client in {@code DiscountComposer} (AIR's
 * BundleDiscountRuleset), which shows the price with the same sum, so the buyer pays what they were shown. Settings
 * {@code discount.*}; values out of range are brought into range so that a typing error cannot stop purchases.
 *
 * @param maxItems the most of one offer bought at once ({@code discount.max.allowed.items}, 1 to {@link #MAX_ITEMS})
 * @param batchSize how many make a batch ({@code discount.batch.size}); 0 turns the discount off
 * @param freePerBatch free items per full batch ({@code discount.batch.free.items}), fewer than a batch
 * @param bonusMinimum full batches before the bonus starts ({@code discount.bonus.min.discounts})
 * @param thresholds one more free item at each of these amounts ({@code discount.additional.thresholds})
 */
public record BundleDiscountRules(int maxItems, int batchSize, int freePerBatch, int bonusMinimum, int[] thresholds) {

    /** The most of one offer the settings may allow at once: each one is a row and an item made in one purchase. */
    public static final int MAX_ITEMS = 1000;

    public static final BundleDiscountRules DEFAULT = new BundleDiscountRules(100, 6, 1, 1, new int[] {40, 99});

    public BundleDiscountRules {
        maxItems = clamp(maxItems, 1, MAX_ITEMS);
        batchSize = clamp(batchSize, 0, MAX_ITEMS);
        freePerBatch = clamp(freePerBatch, 0, Math.max(0, batchSize - 1));
        bonusMinimum = clamp(bonusMinimum, 0, MAX_ITEMS);
        thresholds = thresholds == null
                ? new int[0]
                : Arrays.stream(thresholds)
                        .filter(t -> t > 0 && t <= MAX_ITEMS)
                        .sorted()
                        .distinct()
                        .toArray();
    }

    /**
     * The free items among {@code amount}, AIR's sum (and Octane's {@code getCatalogBundleDiscountItemCount}): a full
     * batch gives {@code freePerBatch}; from {@code bonusMinimum} batches on, one batch short of the next counts once
     * more and each batch above the minimum gives one more; each threshold reached gives one more. With no batch size
     * nothing is free.
     */
    public int freeItems(int amount) {
        if (amount <= 0 || this.batchSize <= 0) return 0;

        int batches = amount / this.batchSize;
        int free = batches * this.freePerBatch;

        if (batches >= this.bonusMinimum) {
            if (amount % this.batchSize == this.batchSize - 1) free++;
            free += batches - this.bonusMinimum;
        }

        for (int threshold : this.thresholds) {
            if (amount >= threshold) free++;
        }

        return Math.min(free, amount);
    }

    /** The amount paid for: {@code amount} less its free items. */
    public int payableItems(int amount) {
        return Math.max(0, amount - this.freeItems(amount));
    }

    /**
     * The thresholds written as {@code 40;99}: separated by {@code ;} or {@code ,}; blanks and what is not a whole
     * number are skipped, so one typing error does not drop the others. An empty text is no thresholds.
     *
     * @throws IllegalArgumentException when the text has parts but none is a number, so the setting keeps its value
     */
    public static int[] parseThresholds(String text) {
        TreeSet<Integer> thresholds = new TreeSet<>();
        if (text == null || text.isBlank()) return new int[0];

        for (String part : text.split("[;,]")) {
            try {
                thresholds.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // Not a number: skipped.
            }
        }

        if (thresholds.isEmpty()) {
            throw new IllegalArgumentException("no whole number in '" + text + "'");
        }
        return thresholds.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BundleDiscountRules rules
                && this.maxItems == rules.maxItems
                && this.batchSize == rules.batchSize
                && this.freePerBatch == rules.freePerBatch
                && this.bonusMinimum == rules.bonusMinimum
                && Arrays.equals(this.thresholds, rules.thresholds);
    }

    @Override
    public int hashCode() {
        return 31 * java.util.Objects.hash(this.maxItems, this.batchSize, this.freePerBatch, this.bonusMinimum)
                + Arrays.hashCode(this.thresholds);
    }

    @Override
    public String toString() {
        return "BundleDiscountRules[maxItems=" + this.maxItems + ", batchSize=" + this.batchSize + ", freePerBatch="
                + this.freePerBatch + ", bonusMinimum=" + this.bonusMinimum + ", thresholds="
                + Arrays.toString(this.thresholds) + "]";
    }
}
