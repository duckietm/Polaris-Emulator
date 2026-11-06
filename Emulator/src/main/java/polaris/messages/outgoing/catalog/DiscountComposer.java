package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class DiscountComposer extends MessageComposer {
    public static volatile int MAXIMUM_ALLOWED_ITEMS = 100;
    public static volatile int DISCOUNT_BATCH_SIZE = 6;
    public static volatile int DISCOUNT_AMOUNT_PER_BATCH = 1;
    public static volatile int MINIMUM_DISCOUNTS_FOR_BONUS = 1;
    public static volatile int[] ADDITIONAL_DISCOUNT_THRESHOLDS = new int[] {40, 99};

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.DiscountComposer);

        this.response.appendInt(MAXIMUM_ALLOWED_ITEMS);
        this.response.appendInt(DISCOUNT_BATCH_SIZE);
        this.response.appendInt(DISCOUNT_AMOUNT_PER_BATCH);
        this.response.appendInt(MINIMUM_DISCOUNTS_FOR_BONUS);

        this.response.appendInt(ADDITIONAL_DISCOUNT_THRESHOLDS.length);
        for (int threshold : ADDITIONAL_DISCOUNT_THRESHOLDS) {
            this.response.appendInt(threshold);
        }

        return this.response;
    }
}
