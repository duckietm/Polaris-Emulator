package com.eu.habbo.messages.incoming.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketplaceInputContractTest {
    @Test
    void marketplaceIdHandlersRejectNonPositiveIds() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/marketplace");

        for (String handler : List.of(
                "BuyItemEvent.java", "RequestItemInfoEvent.java", "SellItemEvent.java", "TakeBackItemEvent.java")) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(
                    source.contains("MarketplaceInputGuard.isPositiveId"),
                    handler + " must reject zero or negative ids before marketplace or inventory lookup");
        }
    }

    @Test
    void offerSearchNormalizesCacheKeyInputs() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/marketplace/RequestOffersEvent.java"));

        assertTrue(
                source.contains("MarketplaceInputGuard.normalizeMinPrice"),
                "marketplace offer search must normalize minimum price");
        assertTrue(
                source.contains("MarketplaceInputGuard.normalizeMaxPrice"),
                "marketplace offer search must normalize maximum price");
        assertTrue(
                source.contains("MarketplaceInputGuard.normalizeSearch"),
                "marketplace offer search must trim and bound search text");
        assertTrue(
                source.contains("MarketplaceInputGuard.normalizeSort"),
                "marketplace offer search must normalize sort before using it as a cache key");
    }

    @Test
    void takeBackDoesNotFirePluginEventForMissingOffer() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java"));

        assertTrue(
                source.contains("if (offer == null)"),
                "takeBackItem must ignore missing offers before constructing plugin events");
    }

    @Test
    void itemStatsEchoTheRequestedCategoryAndRunOldestDayFirst() throws Exception {
        String handler = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/marketplace/RequestItemInfoEvent.java"));
        String marketPlace =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java"));

        assertTrue(
                handler.contains("int category = this.packet.readInt();")
                        && handler.contains("new MarketplaceItemInfoComposer(category, id)"),
                "the item stats answer must carry the category the client asked for (it drops mismatches)");
        assertTrue(
                marketPlace.contains("message.appendInt(category);"),
                "serializeItemInfo must echo the requested category instead of always 1");
        assertTrue(
                marketPlace.contains("ORDER BY DATE(from_unixtime(marketplace_items.timestamp)) ASC"),
                "the price history is drawn as one line, so its rows must run oldest day first");
    }
}
