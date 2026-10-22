package com.eu.habbo.messages.outgoing.collectibles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.AvatarOutfit;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.WardrobeOutfit;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The avatar collectibles packets in AIR's field order ({@code NftWardrobeItem}, {@code class_4190}). */
class NftWardrobeWireContractTest {
    @Test
    void usesTheOfficialHeaders() {
        assertEquals(3206, Incoming.GetUserNftWardrobeEvent);
        assertEquals(133, Incoming.SaveUserNftWardrobeEvent);
        assertEquals(3810, Incoming.GetSelectedNftWardrobeOutfitEvent);
        assertEquals(2236, Outgoing.UserNftWardrobeComposer);
        assertEquals(350, Outgoing.SelectedNftWardrobeOutfitComposer);
    }

    @Test
    void wardrobeItemsAreIdFigureGenderTokenAndContract() {
        WardrobeOutfit outfit = new WardrobeOutfit(
                new Owned(5_000_000_001L, 7, "nft_punk", 3),
                new AvatarOutfit("nft_punk", "hd-180-1.hr-100-61", "f", "habbo:avatar_genesis"));
        ByteBuf packet = new UserNftWardrobeComposer(List.of(outfit)).compose().get();
        try {
            packet.skipBytes(4);
            assertEquals(2236, packet.readShort());
            assertEquals(1, packet.readInt());
            assertEquals("5000000001", string(packet));
            assertEquals("hd-180-1.hr-100-61", string(packet));
            assertEquals("F", string(packet));
            assertEquals("5000000001", string(packet));
            assertEquals("habbo:avatar_genesis", string(packet));
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void selectedOutfitIsTokenThenFallbackLook() {
        ByteBuf packet = new SelectedNftWardrobeOutfitComposer("42", "hd-180-1", "M")
                .compose()
                .get();
        try {
            packet.skipBytes(4);
            assertEquals(350, packet.readShort());
            assertEquals("42", string(packet));
            assertEquals("hd-180-1", string(packet));
            assertEquals("M", string(packet));
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    private static String string(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
