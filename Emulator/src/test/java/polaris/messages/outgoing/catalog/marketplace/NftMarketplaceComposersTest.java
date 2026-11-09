package polaris.messages.outgoing.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.catalog.marketplace.MarketPlaceOffer;
import polaris.hotel.catalog.marketplace.NftMarketplace;

/** The Collectibles marketplace packets, in the field order AIR's parsers read. */
class NftMarketplaceComposersTest {

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        byte[] bytes = new byte[length];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    void theBuyResultCarriesTheResultALongOfferIdAndThePrice() {
        ByteBuf packet = new NftMarketplaceBuyResultComposer(new NftMarketplace.BuyResult("SUCCESS", 41, 120))
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals("SUCCESS", readString(packet));
        assertEquals(41L, packet.readLong());
        assertEquals(120, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void theMakeOfferResultIsOneInt() {
        ByteBuf packet = new NftMarketplaceMakeOfferResultComposer(1).compose().get();
        packet.skipBytes(6);

        assertEquals(1, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void theItemStatsEndWithTheLowestAndSuggestedPrice() {
        NftMarketplace.ItemStats stats =
                new NftMarketplace.ItemStats(80, 3, 30, List.of(new NftMarketplace.HistoryDay(-2, 75, 4)), 70, 80);
        ByteBuf packet = new NftMarketplaceItemStatsComposer(stats).compose().get();
        packet.skipBytes(6);

        assertEquals(80, packet.readInt());
        assertEquals(3, packet.readInt());
        assertEquals(30, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals(-2, packet.readInt());
        assertEquals(75, packet.readInt());
        assertEquals(4, packet.readInt());
        assertEquals(70, packet.readInt());
        assertEquals(80, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void aCollectibleOfferIsTypeFiveWithItsProductCodeAndTheAskingPrice() {
        MarketPlaceOffer offer = mock(MarketPlaceOffer.class);
        when(offer.getOfferId()).thenReturn(41);
        when(offer.getType()).thenReturn(5);
        when(offer.getProductCode()).thenReturn("nft_h23_bunny");
        when(offer.getItemId()).thenReturn(4521);
        when(offer.getNftFurniType()).thenReturn(1);
        when(offer.getBuyerPrice()).thenReturn(100);
        when(offer.getBuyerAverage()).thenReturn(90);

        ByteBuf packet = new MarketplaceOffersComposer(List.of(offer)).compose().get();
        packet.skipBytes(6);

        assertEquals(1, packet.readInt());
        assertEquals(41, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals(5, packet.readInt());
        assertEquals("nft_h23_bunny", readString(packet));
        assertEquals(4521, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals(100, packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(90, packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(1, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void theConfigurationSendsAIRsFeeFieldsAndTheCollectiblesPart() {
        ByteBuf packet = new MarketplaceConfigComposer().compose().get();
        packet.skipBytes(6);

        assertEquals(1, packet.readByte());
        for (int i = 0; i < 7; i++) packet.readInt();
        assertEquals(0, packet.readInt());
        assertEquals(1_000_000, packet.readInt());
        assertEquals(Integer.MAX_VALUE, packet.readInt());
        // No configuration in a unit test: the collectibles part is off.
        assertEquals(0, packet.readByte());
        for (int i = 0; i < 6; i++) packet.readInt();
        assertFalse(packet.isReadable());
    }
}
