package polaris.hotel.catalog.marketplace;

import polaris.core.ConfigurationManager;

/**
 * The Collectibles marketplace (AIR offer type 5): {@code nft_*} furni sold for emeralds. Off unless the hotel sets
 * {@code hotel.marketplace.nft.enabled}. The buyer pays the asking price; the seller receives it minus AIR's fee
 * ({@code MarketplaceView.calculateFinalPrice}) at the moment of the sale.
 */
public record NftMarketplaceSettings(
        boolean enabled,
        int minPrice,
        int maxPrice,
        int offerMinutes,
        int maxOpenOffers,
        int feePercent,
        int halfTaxLimit,
        int emeraldType) {
    public static final String ENABLED = "hotel.marketplace.nft.enabled";
    public static final String MIN_PRICE = "hotel.marketplace.nft.price.min";
    public static final String MAX_PRICE = "hotel.marketplace.nft.price.max";
    public static final String OFFER_MINUTES = "hotel.marketplace.nft.offer.minutes";
    public static final String MAX_OPEN_OFFERS = "hotel.marketplace.nft.offers.max";
    public static final String FEE_PERCENT = "hotel.marketplace.nft.fee.percent";
    public static final String HALF_TAX_LIMIT = "hotel.marketplace.nft.halftax.limit";
    public static final String EMERALD_TYPE = "hotel.collectibles.currency.emerald";

    /** A collectible is a furni whose class name starts with this, as in AIR ({@code GroupItem.isNft}). */
    public static final String PRODUCT_PREFIX = "nft_";

    public NftMarketplaceSettings {
        minPrice = Math.max(1, minPrice);
        maxPrice = Math.max(minPrice, Math.min(maxPrice, MarketPlace.MAXIMUM_LISTING_PRICE));
        offerMinutes = Math.max(1, offerMinutes);
        maxOpenOffers = Math.max(1, maxOpenOffers);
        feePercent = Math.max(0, Math.min(100, feePercent));
        halfTaxLimit = Math.max(0, halfTaxLimit);
    }

    public static NftMarketplaceSettings from(ConfigurationManager configuration) {
        return new NftMarketplaceSettings(
                configuration.getBoolean(ENABLED, false),
                configuration.getInt(MIN_PRICE, 1),
                configuration.getInt(MAX_PRICE, 100_000),
                configuration.getInt(OFFER_MINUTES, 10_080),
                configuration.getInt(MAX_OPEN_OFFERS, 25),
                configuration.getInt(FEE_PERCENT, 5),
                configuration.getInt(HALF_TAX_LIMIT, 0),
                configuration.getInt(EMERALD_TYPE, 1001));
    }

    /** The live settings; off before start-up finished. */
    public static NftMarketplaceSettings current() {
        NftMarketplaceRuntime runtime = NftMarketplaceRuntime.installed();
        return runtime == null
                ? new NftMarketplaceSettings(false, 1, 1, 1, 1, 0, 0, 1001)
                : runtime.settings().get();
    }

    /** The marketplace and its collectibles part are both on. */
    public boolean active() {
        return this.enabled && MarketPlace.MARKETPLACE_ENABLED;
    }

    public boolean isValidPrice(int price) {
        return price >= this.minPrice && price <= this.maxPrice;
    }

    public int offerSeconds() {
        return (int) Math.min(Integer.MAX_VALUE, this.offerMinutes * 60L);
    }

    /** AIR's fee: {@code ceil(round(1000 * price * (fee% + price / (2 * halfTaxLimit))) / 1000)}, never above the price. */
    public int fee(int price) {
        if (price <= 0) return 0;
        double rate = this.feePercent / 100.0 + (this.halfTaxLimit > 0 ? 0.5 * price / this.halfTaxLimit : 0);
        long fee = (long) Math.ceil(Math.round(1000 * (price * rate)) / 1000.0);
        return (int) Math.max(0, Math.min(price, fee));
    }

    /** What the seller receives for a sale at this price. */
    public int payout(int price) {
        return price - this.fee(price);
    }

    public static boolean isProduct(String className) {
        return className != null && className.startsWith(PRODUCT_PREFIX);
    }
}
