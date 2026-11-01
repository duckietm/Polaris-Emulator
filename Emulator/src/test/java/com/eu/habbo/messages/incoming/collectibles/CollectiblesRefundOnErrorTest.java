package com.eu.habbo.messages.incoming.collectibles;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The currency was refunded only when the service answered "nothing"; when it threw, after the emeralds, the
 * silver or the stamps were already taken, the exception went up and nothing came back.
 */
class CollectiblesRefundOnErrorTest {
    private static final Path MAIN = Path.of("src/main/java/com/eu/habbo");

    private static String source(String path) throws Exception {
        return Files.readString(MAIN.resolve(path)).replaceAll("\\s+", " ");
    }

    @Test
    void aShopPurchaseThatThrowsRefundsTheEmeralds() throws Exception {
        String source = source("messages/incoming/collectibles/PurchaseCollectibleEvent.java");

        assertTrue(source.contains("} catch (Exception exception) { // The emeralds are already taken"));
        assertTrue(
                source.contains(
                        "CollectiblesSupport.refund(habbo, settings.emeraldType(), price, \"collectibles.shop.refund\"); this.client.sendResponse(new CollectiblePurchaseResultComposer(1)); throw exception;"));
    }

    @Test
    void aStampPurchaseThatThrowsRefundsTheSilver() throws Exception {
        String source = source("messages/incoming/collectibles/PurchaseMintTokensEvent.java");

        assertTrue(
                source.contains(
                        "CollectiblesSupport.refund(habbo, settings.silverType(), price, \"collectibles.stamps.refund\"); this.client.sendResponse(new CollectiblePurchaseResultComposer(1)); throw exception;"));
    }

    @Test
    void aRelicMintThatThrowsGivesTheStampsBack() throws Exception {
        String source = source("habbohotel/collectibles/CollectiblesService.java");

        assertTrue(source.contains("} catch (SQLException e) { // The stamps are already taken"));
        assertTrue(source.contains("if (price > 0) this.addMintTokens(userId, price); throw e;"));
    }
}
