package polaris.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.catalog.TargetOffer;
import polaris.messages.incoming.hotelview.HotelViewRequestLTDAvailabilityEvent;
import polaris.messages.outgoing.catalog.DiscountComposer;

class ConfigurationBindingBehaviorTest {

    private ConfigurationManager originalConfig;
    private int originalMaximumItems;
    private int originalBatchSize;
    private int originalFreeItems;
    private int originalMinimumBonus;
    private int[] originalDiscountThresholds;
    private boolean originalLtdEnabled;
    private int originalLtdTimestamp;
    private int originalLtdItemId;
    private int originalLtdPageId;
    private String originalLtdItemName;
    private int originalTargetOffer;

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void installConfiguration() throws Exception {
        Field field = Emulator.class.getDeclaredField("config");
        field.setAccessible(true);
        this.originalConfig = (ConfigurationManager) field.get(null);
        this.originalMaximumItems = DiscountComposer.MAXIMUM_ALLOWED_ITEMS;
        this.originalBatchSize = DiscountComposer.DISCOUNT_BATCH_SIZE;
        this.originalFreeItems = DiscountComposer.DISCOUNT_AMOUNT_PER_BATCH;
        this.originalMinimumBonus = DiscountComposer.MINIMUM_DISCOUNTS_FOR_BONUS;
        this.originalDiscountThresholds = DiscountComposer.ADDITIONAL_DISCOUNT_THRESHOLDS;
        this.originalLtdEnabled = HotelViewRequestLTDAvailabilityEvent.ENABLED;
        this.originalLtdTimestamp = HotelViewRequestLTDAvailabilityEvent.TIMESTAMP;
        this.originalLtdItemId = HotelViewRequestLTDAvailabilityEvent.ITEM_ID;
        this.originalLtdPageId = HotelViewRequestLTDAvailabilityEvent.PAGE_ID;
        this.originalLtdItemName = HotelViewRequestLTDAvailabilityEvent.ITEM_NAME;
        this.originalTargetOffer = TargetOffer.ACTIVE_TARGET_OFFER_ID;

        Path config = this.tempDirectory.resolve("config.ini");
        Files.writeString(
                config,
                "discount.additional.thresholds=invalid\n"
                        + "hotel.view.ltdcountdown.itemname=test\n"
                        + "hotel.targetoffer.id=214\n");
        field.set(null, new ConfigurationManager(config.toString()));
    }

    @AfterEach
    void restoreConfiguration() throws Exception {
        Field field = Emulator.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(null, this.originalConfig);
        DiscountComposer.MAXIMUM_ALLOWED_ITEMS = this.originalMaximumItems;
        DiscountComposer.DISCOUNT_BATCH_SIZE = this.originalBatchSize;
        DiscountComposer.DISCOUNT_AMOUNT_PER_BATCH = this.originalFreeItems;
        DiscountComposer.MINIMUM_DISCOUNTS_FOR_BONUS = this.originalMinimumBonus;
        DiscountComposer.ADDITIONAL_DISCOUNT_THRESHOLDS = this.originalDiscountThresholds;
        HotelViewRequestLTDAvailabilityEvent.ENABLED = this.originalLtdEnabled;
        HotelViewRequestLTDAvailabilityEvent.TIMESTAMP = this.originalLtdTimestamp;
        HotelViewRequestLTDAvailabilityEvent.ITEM_ID = this.originalLtdItemId;
        HotelViewRequestLTDAvailabilityEvent.PAGE_ID = this.originalLtdPageId;
        HotelViewRequestLTDAvailabilityEvent.ITEM_NAME = this.originalLtdItemName;
        TargetOffer.ACTIVE_TARGET_OFFER_ID = this.originalTargetOffer;
    }

    @Test
    void malformedValueDoesNotPreventLaterKeysFromApplying() {
        assertDoesNotThrow(() -> new CatalogConfigurationBinder(Emulator.getConfig(), false).bindDiscountsAndOffers());
        assertEquals(214, TargetOffer.ACTIVE_TARGET_OFFER_ID);
        assertSame(this.originalDiscountThresholds, DiscountComposer.ADDITIONAL_DISCOUNT_THRESHOLDS);
    }
}
