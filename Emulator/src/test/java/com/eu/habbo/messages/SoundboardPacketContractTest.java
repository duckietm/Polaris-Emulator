package com.eu.habbo.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.soundboard.SoundboardCatalogResult;
import com.eu.habbo.habbohotel.soundboard.SoundboardSound;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardCatalogComposer;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardCatalogResultComposer;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardPlayComposer;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardPlayDeniedComposer;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardSettingsComposer;
import com.eu.habbo.messages.outgoing.users.MeMenuSettingsComposer;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SoundboardPacketContractTest {

    @Test
    void settingsCarryPersonalizedCooldownBeforeTheFilteredSounds() {
        SoundboardSound bell = new SoundboardSound(7, "Campanella", "/sounds/soundboard/campanella.mp3", 1);
        ByteBuf packet = new SoundboardSettingsComposer(true, 60, List.of(bell))
                .compose()
                .get();
        packet.skipBytes(6);

        assertTrue(packet.readBoolean());
        assertEquals(60, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals(7, packet.readInt());
        assertEquals("Campanella", readString(packet));
        assertEquals("/sounds/soundboard/campanella.mp3", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void playCarriesAuthoritativeSoundAndActorMetadata() {
        ByteBuf packet = new SoundboardPlayComposer(
                        7, "/sounds/soundboard/campanella.mp3", "Campanella", 42, 3, "Simoleo")
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals(7, packet.readInt());
        assertEquals("/sounds/soundboard/campanella.mp3", readString(packet));
        assertEquals("Campanella", readString(packet));
        assertEquals(42, packet.readInt());
        assertEquals(3, packet.readInt());
        assertEquals("Simoleo", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void userSettingsAppendSoundboardVolumeAfterExistingFields() {
        Habbo habbo = mock(Habbo.class);
        HabboStats stats = mock(HabboStats.class);
        stats.volumeSystem = 10;
        stats.volumeFurni = 20;
        stats.volumeTrax = 30;
        stats.volumeSoundboard = 40;
        stats.preferOldChat = true;
        stats.blockRoomInvites = false;
        stats.blockCameraFollow = true;
        stats.uiFlags = 12;
        stats.chatColor = RoomChatMessageBubbles.BLUE;
        stats.hideOnline = false;
        stats.blockFollowing = true;
        stats.blockFriendRequests = false;
        when(habbo.getHabboStats()).thenReturn(stats);

        ByteBuf packet = new MeMenuSettingsComposer(habbo).compose().get();
        packet.skipBytes(6);

        assertEquals(10, packet.readInt());
        assertEquals(20, packet.readInt());
        assertEquals(30, packet.readInt());
        assertTrue(packet.readBoolean());
        assertFalse(packet.readBoolean());
        assertTrue(packet.readBoolean());
        assertEquals(12, packet.readInt());
        assertEquals(RoomChatMessageBubbles.BLUE.getType(), packet.readInt());
        assertTrue(packet.readBoolean());
        assertFalse(packet.readBoolean());
        assertTrue(packet.readBoolean());
        assertEquals(40, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void playDenialCarriesStableReasonAndRemainingCooldown() {
        ByteBuf packet = new SoundboardPlayDeniedComposer(SoundboardPlayDeniedComposer.Reason.COOLDOWN, 13)
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals(1, packet.readInt());
        assertEquals(13, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void catalogCarriesEnabledAndDisabledManagementFields() {
        SoundboardSound disabled = new SoundboardSound(7, "Campanella", "/sounds/bell.mp3", false, 20, 5);
        ByteBuf packet =
                new SoundboardCatalogComposer(List.of(disabled)).compose().get();
        packet.skipBytes(6);

        assertEquals(1, packet.readInt());
        assertEquals(7, packet.readInt());
        assertEquals("Campanella", readString(packet));
        assertEquals("/sounds/bell.mp3", readString(packet));
        assertFalse(packet.readBoolean());
        assertEquals(20, packet.readInt());
        assertEquals(5, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void catalogResultCarriesOperationCodeResultCodeAndSoundId() {
        ByteBuf packet = new SoundboardCatalogResultComposer(
                        SoundboardCatalogResultComposer.Operation.UPSERT,
                        new SoundboardCatalogResult(SoundboardCatalogResult.Code.INVALID_URL, 7))
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals(1, packet.readInt());
        assertEquals(3, packet.readInt());
        assertEquals(7, packet.readInt());
        assertFalse(packet.isReadable());
    }

    private static String readString(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
