package polaris.hotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** AIR's Collectibles marketplace fee ({@code MarketplaceView.calculateFinalPrice}) and the settings' limits. */
class NftMarketplaceSettingsTest {

    private static NftMarketplaceSettings settings(int feePercent, int halfTaxLimit) {
        return new NftMarketplaceSettings(true, 1, 100_000, 10_080, 25, feePercent, halfTaxLimit, 1001);
    }

    @Test
    void takesTheFeePercentageRoundedUp() {
        NftMarketplaceSettings settings = settings(5, 0);

        assertEquals(5, settings.fee(100));
        assertEquals(95, settings.payout(100));
        assertEquals(1, settings.fee(1));
        assertEquals(0, settings.payout(1));
        assertEquals(6, settings.fee(101));
    }

    @Test
    void addsHalfThePriceOverTheHalfTaxLimitAsAIRDoes() {
        // 100 * (0 + 0.5 * 100 / 1000) = 5
        assertEquals(5, settings(0, 1000).fee(100));
        // 2000 * (0.05 + 0.5 * 2000 / 1000) = 2100, never more than the price
        assertEquals(2000, settings(5, 1000).fee(2000));
        assertEquals(0, settings(5, 1000).payout(2000));
    }

    @Test
    void keepsTheLimitsUsable() {
        NftMarketplaceSettings settings = new NftMarketplaceSettings(true, 0, -5, 0, 0, 250, -1, 1001);

        assertEquals(1, settings.minPrice());
        assertEquals(1, settings.maxPrice());
        assertEquals(1, settings.offerMinutes());
        assertEquals(1, settings.maxOpenOffers());
        assertEquals(100, settings.feePercent());
        assertEquals(0, settings.halfTaxLimit());
        assertEquals(60, settings.offerSeconds());
    }

    @Test
    void checksTheAskingPriceRange() {
        NftMarketplaceSettings settings = new NftMarketplaceSettings(true, 10, 50, 60, 5, 5, 0, 1001);

        assertFalse(settings.isValidPrice(9));
        assertTrue(settings.isValidPrice(10));
        assertTrue(settings.isValidPrice(50));
        assertFalse(settings.isValidPrice(51));
    }

    @Test
    void aCollectibleIsAnNftFurni() {
        assertTrue(NftMarketplaceSettings.isProduct("nft_h23_bunny"));
        assertFalse(NftMarketplaceSettings.isProduct("rare_dragonlamp"));
        assertFalse(NftMarketplaceSettings.isProduct(null));
    }
}
