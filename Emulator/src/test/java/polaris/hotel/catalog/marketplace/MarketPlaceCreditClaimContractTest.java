package polaris.hotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MarketPlaceCreditClaimContractTest {
    private static String marketPlaceSource() throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/hotel/catalog/marketplace/MarketPlace.java"));
    }

    @Test
    void aTakenBackOfferReturnsItsItemInTheSameTransaction() throws Exception {
        String source = marketPlaceSource();
        int helper = source.indexOf("private static boolean returnOfferItem(");
        int delete = source.indexOf("DELETE FROM marketplace_items WHERE id = ? AND state != 2", helper);
        int update = source.indexOf("updateItems.executeUpdate() != 1", helper);
        int commit = source.indexOf("connection.commit();", helper);

        assertTrue(helper > 0 && delete > helper && update > delete && commit > update);
        assertTrue(source.indexOf("returnOfferItem(connection, user, offer)")
                < source.indexOf("user.getInventory().removeMarketplaceOffer(offer);"));
    }

    @Test
    void aSaleIsShownGoneOnlyOnceTheOfferIsSaved() throws Exception {
        String source = marketPlaceSource();
        int persisted = source.indexOf("if (!offer.isPersisted()) {");
        int removal = source.indexOf("new RemoveUserItemComposer(clientItemId)");

        assertTrue(persisted > 0 && removal > persisted);
    }
}
