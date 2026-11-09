package polaris.plugin;

import java.util.Arrays;
import polaris.core.ConfigurationManager;
import polaris.core.config.ConfigurationBinder;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.catalog.BundleDiscountRules;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.catalog.TargetOffer;
import polaris.hotel.catalog.marketplace.MarketPlace;
import polaris.hotel.items.ItemManager;
import polaris.messages.incoming.hotelview.HotelViewRequestLTDAvailabilityEvent;
import polaris.messages.outgoing.catalog.DiscountComposer;
import polaris.messages.outgoing.catalog.GiftConfigurationComposer;

final class CatalogConfigurationBinder extends ConfigurationBinder {

    private final boolean runtimeReady;

    CatalogConfigurationBinder(ConfigurationManager configuration, boolean runtimeReady) {
        super(configuration);
        this.runtimeReady = runtimeReady;
    }

    void bind() {
        this.apply(
                "hotel.catalog.recycler.enabled",
                () -> ItemManager.RECYCLER_ENABLED = this.configuration.getBoolean("hotel.catalog.recycler.enabled"));
        this.apply(
                "hotel.marketplace.enabled",
                () -> MarketPlace.MARKETPLACE_ENABLED = this.configuration.getBoolean("hotel.marketplace.enabled"));
        this.apply(
                "hotel.marketplace.currency",
                () -> MarketPlace.MARKETPLACE_CURRENCY = this.configuration.getInt("hotel.marketplace.currency"));
        this.bindDiscountsAndOffers();
        this.apply(
                "hotel.catalog.purchase.cooldown",
                () -> CatalogManager.PURCHASE_COOLDOWN = this.configuration.getInt("hotel.catalog.purchase.cooldown"));
        this.apply(
                "hotel.catalog.items.display.ordernum",
                () -> CatalogManager.SORT_USING_ORDERNUM =
                        this.configuration.getBoolean("hotel.catalog.items.display.ordernum"));
        this.apply(
                "hotel.talenttrack.enabled",
                () -> AchievementManager.TALENTTRACK_ENABLED =
                        this.configuration.getBoolean("hotel.talenttrack.enabled"));

        if (this.runtimeReady) {
            this.apply("hotel.gifts.box_types", () -> {
                var boxTypes = Arrays.stream(this.configuration
                                .getValue("hotel.gifts.box_types")
                                .split(","))
                        .mapToInt(Integer::parseInt)
                        .boxed()
                        .toList();
                GiftConfigurationComposer.BOX_TYPES = boxTypes;
            });
            this.apply("hotel.gifts.ribbon_types", () -> {
                var ribbonTypes = Arrays.stream(this.configuration
                                .getValue("hotel.gifts.ribbon_types")
                                .split(","))
                        .mapToInt(Integer::parseInt)
                        .boxed()
                        .toList();
                GiftConfigurationComposer.RIBBON_TYPES = ribbonTypes;
            });
        }
    }

    void bindDiscountsAndOffers() {
        this.apply(
                "discount.max.allowed.items",
                () -> DiscountComposer.MAXIMUM_ALLOWED_ITEMS =
                        this.configuration.getInt("discount.max.allowed.items", 100));
        this.apply(
                "discount.batch.size",
                () -> DiscountComposer.DISCOUNT_BATCH_SIZE = this.configuration.getInt("discount.batch.size", 6));
        this.apply(
                "discount.batch.free.items",
                () -> DiscountComposer.DISCOUNT_AMOUNT_PER_BATCH =
                        this.configuration.getInt("discount.batch.free.items", 1));
        this.apply(
                "discount.bonus.min.discounts",
                () -> DiscountComposer.MINIMUM_DISCOUNTS_FOR_BONUS =
                        this.configuration.getInt("discount.bonus.min.discounts", 1));
        this.apply(
                "discount.additional.thresholds",
                () -> DiscountComposer.ADDITIONAL_DISCOUNT_THRESHOLDS = BundleDiscountRules.parseThresholds(
                        this.configuration.getValue("discount.additional.thresholds", "40;99")));
        this.apply(
                "hotel.view.ltdcountdown.enabled",
                () -> HotelViewRequestLTDAvailabilityEvent.ENABLED =
                        this.configuration.getBoolean("hotel.view.ltdcountdown.enabled"));
        this.apply(
                "hotel.view.ltdcountdown.timestamp",
                () -> HotelViewRequestLTDAvailabilityEvent.TIMESTAMP =
                        this.configuration.getInt("hotel.view.ltdcountdown.timestamp"));
        this.apply(
                "hotel.view.ltdcountdown.itemid",
                () -> HotelViewRequestLTDAvailabilityEvent.ITEM_ID =
                        this.configuration.getInt("hotel.view.ltdcountdown.itemid"));
        this.apply(
                "hotel.view.ltdcountdown.pageid",
                () -> HotelViewRequestLTDAvailabilityEvent.PAGE_ID =
                        this.configuration.getInt("hotel.view.ltdcountdown.pageid"));
        this.apply(
                "hotel.view.ltdcountdown.itemname",
                () -> HotelViewRequestLTDAvailabilityEvent.ITEM_NAME =
                        this.configuration.getValue("hotel.view.ltdcountdown.itemname"));
        this.apply(
                "hotel.targetoffer.id",
                () -> TargetOffer.ACTIVE_TARGET_OFFER_ID = this.configuration.getInt("hotel.targetoffer.id"));
    }
}
