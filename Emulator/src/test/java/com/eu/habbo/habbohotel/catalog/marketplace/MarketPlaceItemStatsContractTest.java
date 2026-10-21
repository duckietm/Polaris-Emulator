package com.eu.habbo.habbohotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MarketPlaceItemStatsContractTest {

    @Test
    void suggestedPricePrefersRecentSalesThenLowestOpenOffer() {
        assertEquals(120, MarketPlace.suggestedPrice(120, 90));
        assertEquals(90, MarketPlace.suggestedPrice(0, 90));
        assertEquals(0, MarketPlace.suggestedPrice(0, 0));
        assertEquals(0, MarketPlace.suggestedPrice(-5, -1));
    }

    @Test
    void lowestAndSuggestedPriceFollowTheEchoedCategoryAndSpriteId() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java"));
        int start =
                source.indexOf("public static void serializeItemInfo(int category, int itemId, ServerMessage message)");
        int category = source.indexOf("message.appendInt(category);", start);
        int itemId = source.indexOf("message.appendInt(itemId);", category);
        int lowest = source.indexOf("message.appendInt(lowestOpenPrice > 0", itemId);
        int suggested =
                source.indexOf("message.appendInt(suggestedPrice(recentSalesAverage, lowestOpenPrice));", lowest);

        assertTrue(start > -1, "serializeItemInfo(category, itemId, message) must exist");
        assertTrue(
                category > start && itemId > category && lowest > itemId && suggested > lowest,
                "AIR reads category, sprite id, lowest current price and suggested price in that order");
    }
}
