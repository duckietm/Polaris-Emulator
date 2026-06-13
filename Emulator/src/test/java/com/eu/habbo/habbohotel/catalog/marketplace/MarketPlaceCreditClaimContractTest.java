package com.eu.habbo.habbohotel.catalog.marketplace;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPlaceCreditClaimContractTest {
    private static String marketPlaceSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java"));
    }

    @Test
    void soldOfferIsDetachedBeforeCreditsAreGranted() throws Exception {
        String source = marketPlaceSource();
        int getCreditsStart = source.indexOf("public static void getCredits");
        int removeUserCall = source.indexOf("removeUser(offer)", getCreditsStart);
        int creditAccumulator = source.indexOf("credits += offer.getPrice()", getCreditsStart);
        int inventoryRemoval = source.indexOf("removeMarketplaceOffer(offer)", getCreditsStart);

        assertTrue(getCreditsStart > -1, "MarketPlace.getCredits must exist");
        assertTrue(removeUserCall > -1, "Sold marketplace offers must be detached in the database");
        assertTrue(removeUserCall < creditAccumulator,
                "Credits must not be granted until the sold offer is detached from the seller in the database");
        assertTrue(removeUserCall < inventoryRemoval,
                "The in-memory sold offer should remain claimable if the database detach fails");
    }

    @Test
    void detachFailureIsObservableByCaller() throws Exception {
        String source = marketPlaceSource();

        assertTrue(source.contains("private static boolean removeUser"),
                "removeUser must report whether the marketplace ownership update succeeded");
    }
}
