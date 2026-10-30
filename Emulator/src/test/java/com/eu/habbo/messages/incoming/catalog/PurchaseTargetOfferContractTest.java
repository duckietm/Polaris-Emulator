package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A targeted offer took its purchase limit before the purchase, which could fail, and charged the catalog item's price
 * instead of the offer's. A purchase without a page could also throw when it named the page.
 */
class PurchaseTargetOfferContractTest {

    private static final Path EVENT =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/PurchaseTargetOfferEvent.java");
    private static final Path CATALOG = Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java");

    @Test
    void theOfferIsChargedAtItsOwnPriceAndTheLimitFollowsDelivery() throws Exception {
        String event = Files.readString(EVENT);

        assertFalse(event.contains("purchaseItem(null, item, this.client.getHabbo(), amount, \"\", false)"));
        assertTrue(event.contains("offer.getPriceInCredits()"));
        assertTrue(event.contains("catalog.purchaseItemDelivered(null, item, habbo, 1, \"\", true)"));

        int deliver = event.indexOf("int delivered = this.buyAtOfferPrice(offer, item, amount);");
        int limit = event.indexOf("if (delivered > 0) purchase.update(delivered, now);");
        assertTrue(deliver > 0 && limit > deliver);
    }

    @Test
    void whatWasNotDeliveredIsGivenBack() throws Exception {
        String event = Files.readString(EVENT);

        assertTrue(event.contains("int missing = amount - delivered;"));
        assertTrue(event.contains("\"catalog.targetoffer.refund\""));
    }

    @Test
    void aPurchaseWithoutAPageDoesNotThrowWhenNamingIt() throws Exception {
        assertTrue(Files.readString(CATALOG).contains("page == null ? \"\" : page.getCaption()"));
    }
}
