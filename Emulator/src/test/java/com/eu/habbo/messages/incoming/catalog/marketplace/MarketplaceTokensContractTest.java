package com.eu.habbo.messages.incoming.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketplaceTokenService;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceConfigComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceSellItemComposer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** AIR selling tokens: can-make-offer answer 4, buy-tokens packet 1866 and the batch in the config packet. */
class MarketplaceTokensContractTest {
    private static final Path HANDLERS = Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/marketplace");

    @Test
    void buyTokensIsTheOfficialHeaderAndIsRegistered() throws Exception {
        assertEquals(1866, Incoming.BuyMarketplaceTokensEvent);
        String packets = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/PacketManager.java"));
        assertTrue(packets.contains(
                "registerHandler(Incoming.BuyMarketplaceTokensEvent, BuyMarketplaceTokensEvent.class)"));
        assertTrue(new BuyMarketplaceTokensEvent().getRatelimit() > 0);
    }

    @Test
    void aSellerWithoutTokensIsAnsweredNoAdsLeft() throws Exception {
        assertEquals(4, MarketplaceSellItemComposer.NO_ADS_LEFT);
        String source = Files.readString(HANDLERS.resolve("RequestSellItemEvent.java"));
        assertTrue(source.contains("MarketplaceSellItemComposer.NO_ADS_LEFT"));
        assertTrue(source.contains("settings().enabled()"));
    }

    @Test
    void everyPostedOfferSpendsOneTokenAndAFailedPostRefundsIt() throws Exception {
        for (String handler : new String[] {"SellItemEvent.java", "SellMultipleItemsEvent.java"}) {
            String source = Files.readString(HANDLERS.resolve(handler));
            assertTrue(source.contains(".spend(userId, 1)"), handler);
            assertTrue(source.contains(".refund(userId, 1)"), handler);
        }
    }

    @Test
    void tokensAreOffUntilTheHotelTurnsThemOn() {
        MarketplaceTokenService.Settings defaults = MarketplaceTokenService.currentSettings();

        if (MarketplaceTokenService.installed() == null) {
            assertFalse(defaults.enabled());
            assertEquals(MarketplaceTokenService.DEFAULT_PRICE, defaults.price());
            assertEquals(MarketplaceTokenService.DEFAULT_AMOUNT, defaults.amount());
        }
        assertEquals(0, new MarketplaceTokenService.Settings(true, -4, 0).price());
        assertEquals(1, new MarketplaceTokenService.Settings(true, -4, 0).amount());
    }

    @Test
    void theConfigPacketCarriesTheTokenBatch() {
        var packet = new MarketplaceConfigComposer().compose().get();
        packet.skipBytes(6);

        assertTrue(packet.readBoolean());
        assertEquals(1, packet.readInt());
        MarketplaceTokenService.Settings settings = MarketplaceTokenService.currentSettings();
        assertEquals(settings.price(), packet.readInt());
        assertEquals(settings.amount(), packet.readInt());
    }
}
