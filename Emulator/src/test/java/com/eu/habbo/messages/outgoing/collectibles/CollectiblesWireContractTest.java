package com.eu.habbo.messages.outgoing.collectibles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Claim;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Collection;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.CollectionState;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Entry;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.LevelReward;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.OpenReward;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The collectibles packets in the official client's field order ({@code class_2508}, {@code class_4341}). */
class CollectiblesWireContractTest {
    private static final Product BEAR =
            new Product("nft_bear", 1, "18", 120, "epic", "", List.of(3001, 3002), "toys", 50, true, 10, 4);

    @Test
    void usesTheOfficialHeadersWhereFree() {
        assertEquals(400, Outgoing.CollectibleWalletsComposer);
        assertEquals(1789, Outgoing.CollectorScoreComposer);
        assertEquals(3495, Outgoing.CollectibleShopOffersComposer);
        assertEquals(2539, Outgoing.CollectiblesInventoryComposer);
        assertEquals(1475, Outgoing.CollectiblesInventoryUpdateComposer);
        assertEquals(3374, Outgoing.CollectibleRewardBoxOpenedComposer);
        assertEquals(9420, Outgoing.CollectibleCollectionsComposer);
        assertEquals(3702, Incoming.GetCollectibleWalletsEvent);
        assertEquals(1542, Incoming.PurchaseCollectibleEvent);
        assertEquals(1857, Incoming.TransferCollectiblesEvent);
        assertEquals(1489, Incoming.OpenCollectibleRewardBoxEvent);
        assertEquals(9420, Incoming.ClaimCollectionRewardEvent);
        assertEquals(9421, Incoming.GetCollectibleTransferFeeEvent);
    }

    @Test
    void inventoryItemsAreALongAssetIdThenTheProductInfo() {
        ByteBuf packet = new CollectiblesInventoryComposer(
                        List.of(new Owned(5_000_000_000L, 1, "nft_bear", 4), new Owned(9, 1, "gone", 1)),
                        Map.of("nft_bear", BEAR))
                .compose()
                .get();
        try {
            packet.skipBytes(4);
            assertEquals(2539, packet.readShort());
            assertEquals(1, packet.readInt());
            assertEquals(5_000_000_000L, packet.readLong());
            readProduct(packet, false);
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void collectionsFollowClass4341() {
        Collection set = new Collection("toys", "Toys", 300, "nft_bear", null, 0, 0);
        CollectionState state = new CollectionState(
                set, List.of(new Entry(BEAR, 2)), 120, 120, true, null, null, BEAR, new Claim("reward:toys", 0, 1, 0));
        ByteBuf packet =
                new CollectibleCollectionsComposer(List.of(state)).compose().get();
        try {
            packet.skipBytes(4);
            assertEquals(9420, packet.readShort());
            assertEquals(1, packet.readInt());
            assertEquals(1, packet.readInt());
            readProduct(packet, true);
            assertEquals("toys", string(packet));
            assertEquals("Toys", string(packet));
            assertEquals(120, packet.readInt());
            assertEquals(120, packet.readInt());
            assertEquals(300, packet.readInt());
            assertFalse(packet.readBoolean());
            assertTrue(packet.readBoolean());
            assertEquals(1, packet.readShort());
            assertEquals("18", string(packet));
            assertEquals(120, packet.readInt());
            assertEquals(0, packet.readInt());
            assertEquals("", string(packet));
            assertEquals(2, packet.readInt());
            packet.skipBytes(8);
            assertEquals("nft_bear", string(packet));
            assertEquals("epic", string(packet));
            assertEquals(-1L, packet.readLong());
            assertEquals(-1L, packet.readLong());
            assertEquals(1, packet.readShort());
            assertEquals("reward:toys", string(packet));
            assertEquals(0, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(0, packet.readShort());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void shopOffersCarryPriceAndMintCount() {
        ByteBuf packet =
                new CollectibleShopOffersComposer(List.of(BEAR)).compose().get();
        try {
            packet.skipBytes(4);
            assertEquals(3495, packet.readShort());
            assertEquals(1, packet.readInt());
            assertEquals("nft_bear", string(packet));
            assertEquals(50, packet.readInt());
            assertFalse(packet.readBoolean());
            assertFalse(packet.readBoolean());
            assertEquals(10, packet.readInt());
            assertEquals(4, packet.readInt());
            readProduct(packet, false);
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void rewardClaimsSendTheSetIdThenItsName() {
        OpenReward open = new OpenReward(new LevelReward(3, 1, "nft_bear", 2, 10, 20), BEAR, 1);
        ByteBuf packet = new CollectibleRewardClaimsComposer(List.of(open), "hotel-1", Map.of("toys", "Toy Box"))
                .compose()
                .get();
        try {
            packet.skipBytes(4);
            assertEquals(3865, packet.readShort());
            assertEquals(1, packet.readInt());
            assertEquals("level:3", string(packet));
            assertEquals(0, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(2, packet.readInt());
            assertEquals(10_000L, packet.readLong());
            assertEquals(20_000L, packet.readLong());
            assertEquals(10_000L, packet.readLong());
            assertEquals(0L, packet.readLong());
            assertEquals("toys", string(packet));
            assertEquals("nft_bear", string(packet));
            assertEquals("hotel-1", string(packet));
            readProduct(packet, false);
            assertEquals("toys", string(packet));
            assertEquals("Toy Box", string(packet));
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    private static void readProduct(ByteBuf packet, boolean withAmount) {
        assertEquals(1, packet.readShort());
        assertEquals("18", string(packet));
        assertEquals(120, packet.readInt());
        if (withAmount) assertEquals(2, packet.readInt());
        assertEquals("", string(packet));
        assertEquals(2, packet.readInt());
        assertEquals(3001, packet.readInt());
        assertEquals(3002, packet.readInt());
        assertEquals("nft_bear", string(packet));
        assertEquals("epic", string(packet));
    }

    private static String string(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
