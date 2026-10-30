package com.eu.habbo.habbohotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MarketPlaceCreditClaimContractTest {
    private static String marketPlaceSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java"));
    }

    @Test
    void soldOfferIsDetachedBeforeCreditsAreGranted() throws Exception {
        String source = marketPlaceSource();
        int getCreditsStart = source.indexOf("public static int claimSoldCredits");
        int payoutRangeGuard = source.indexOf("claimable > Integer.MAX_VALUE", getCreditsStart);
        // The claim now also names the seller it is claiming for; the order it has to
        // keep is the same.
        int removeUserCall = source.indexOf("removeUser(offer,", getCreditsStart);
        int creditAccumulator = source.indexOf(
                "credits = WalletBalanceMath.checkedBalance(credits, offer.getPrice())", getCreditsStart);
        int inventoryRemoval = source.indexOf("removeMarketplaceOffer(offer)", getCreditsStart);

        assertTrue(getCreditsStart > -1, "MarketPlace.claimSoldCredits must exist");
        assertTrue(
                payoutRangeGuard > getCreditsStart && payoutRangeGuard < removeUserCall,
                "Unrepresentable aggregate payouts must be rejected before any offer is claimed");
        assertTrue(removeUserCall > -1, "Sold marketplace offers must be detached in the database");
        assertTrue(
                removeUserCall < creditAccumulator,
                "Credits must not be granted until the sold offer is detached from the seller in the database");
        assertTrue(
                removeUserCall < inventoryRemoval,
                "The in-memory sold offer should remain claimable if the database detach fails");
    }

    @Test
    void detachFailureIsObservableByCaller() throws Exception {
        String source = marketPlaceSource();

        assertTrue(
                source.contains("private static boolean removeUser"),
                "removeUser must report whether the marketplace ownership update succeeded");
    }

    @Test
    void aRefusedPayoutReleasesTheClaims() throws Exception {
        String source = marketPlaceSource();
        int start = source.indexOf("public static int claimSoldCredits");
        int apply = source.indexOf("applyCredits(credits, \"marketplace.payout\"", start);
        int release = source.indexOf("releaseClaim(offer, sellerId)", start);

        assertTrue(apply > start, "the payout reports whether it happened");
        assertTrue(release > apply, "a refused payout hands the offers back");
        assertTrue(source.contains("SET seller_claimed = 0 WHERE id = ? AND user_id = ? AND seller_claimed = 1"));
    }

    @Test
    void aTakenBackOfferReturnsItsItemInTheSameTransaction() throws Exception {
        String source = marketPlaceSource();
        int helper = source.indexOf("private static boolean returnOfferItem(");
        int delete = source.indexOf("DELETE FROM marketplace_items WHERE id = ? AND state != 2", helper);
        int update = source.indexOf("updateItems.executeUpdate() != 1", helper);
        int commit = source.indexOf("connection.commit();", helper);

        assertTrue(helper > 0 && delete > helper && update > delete && commit > update);
        assertTrue(source.indexOf("returnOfferItem(connection, habbo, offer)") < source.indexOf("habbo.getInventory().removeMarketplaceOffer(offer);"));
    }
}
