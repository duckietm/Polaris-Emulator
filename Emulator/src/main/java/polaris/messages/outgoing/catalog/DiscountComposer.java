package polaris.messages.outgoing.catalog;

import polaris.hotel.catalog.BundleDiscountRules;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * The catalogue's bulk discount rules (AIR's BundleDiscountRuleset). The fields are set from the {@code discount.*}
 * settings and stay public for plugins; what is sent and charged is {@link #rules()}, the same values brought into
 * range.
 */
public class DiscountComposer extends MessageComposer {
    public static volatile int MAXIMUM_ALLOWED_ITEMS = 100;
    public static volatile int DISCOUNT_BATCH_SIZE = 6;
    public static volatile int DISCOUNT_AMOUNT_PER_BATCH = 1;
    public static volatile int MINIMUM_DISCOUNTS_FOR_BONUS = 1;
    public static volatile int[] ADDITIONAL_DISCOUNT_THRESHOLDS = new int[] {40, 99};

    /** The rules in force, checked: the client shows prices with these and the purchase charges with them. */
    public static BundleDiscountRules rules() {
        return new BundleDiscountRules(
                MAXIMUM_ALLOWED_ITEMS,
                DISCOUNT_BATCH_SIZE,
                DISCOUNT_AMOUNT_PER_BATCH,
                MINIMUM_DISCOUNTS_FOR_BONUS,
                ADDITIONAL_DISCOUNT_THRESHOLDS);
    }

    @Override
    protected ServerMessage composeInternal() {
        BundleDiscountRules rules = rules();
        this.response.init(Outgoing.DiscountComposer);

        this.response.appendInt(rules.maxItems());
        this.response.appendInt(rules.batchSize());
        this.response.appendInt(rules.freePerBatch());
        this.response.appendInt(rules.bonusMinimum());

        this.response.appendInt(rules.thresholds().length);
        for (int threshold : rules.thresholds()) {
            this.response.appendInt(threshold);
        }

        return this.response;
    }
}
