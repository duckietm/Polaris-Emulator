package com.eu.habbo.messages.incoming.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceSellItemComposer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RequestSellItemEventContractTest {
    @Test
    void closedMarketplaceAnswersNotAllowedAndTheCheckIsRateLimited() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/marketplace/RequestSellItemEvent.java"));

        assertEquals(1, MarketplaceSellItemComposer.ALLOWED);
        assertEquals(2, MarketplaceSellItemComposer.NOT_ALLOWED);
        assertTrue(source.contains("MarketplaceSellItemComposer.NOT_ALLOWED"));
        assertFalse(source.contains("MarketplaceSellItemComposer.NO_TRADE_PASS"));
        assertTrue(new RequestSellItemEvent().getRatelimit() > 0);
    }
}
