package polaris.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.soundboard.SoundboardCatalogResult;
import polaris.hotel.soundboard.SoundboardRoomMode;
import polaris.hotel.soundboard.SoundboardSound;
import polaris.hotel.users.User;
import polaris.hotel.users.UserStats;
import polaris.messages.outgoing.soundboard.SoundboardCatalogComposer;
import polaris.messages.outgoing.soundboard.SoundboardCatalogResultComposer;
import polaris.messages.outgoing.soundboard.SoundboardPlayComposer;
import polaris.messages.outgoing.soundboard.SoundboardPlayDeniedComposer;
import polaris.messages.outgoing.soundboard.SoundboardSettingsComposer;
import polaris.messages.outgoing.users.MeMenuSettingsComposer;

class SoundboardPacketContractTest {

    @Test
    void settingsCarryPersonalizedCooldownBeforeTheFilteredSounds() {
        SoundboardSound bell =
                new SoundboardSound(7, "Campanella", "campanella", "/sounds/soundboard/campanella.mp3", 1);
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
        // classnames follow the records as their own block
        assertEquals("campanella", readString(packet));
        // the room mode comes last, so a client that stops after the classnames still works
        assertEquals(SoundboardRoomMode.EVERYONE.wireCode(), packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void settingsTellTheRoomModeAndKeepTheBooleanOnForAnyModeButOff() {
        for (SoundboardRoomMode mode : SoundboardRoomMode.values()) {
            ByteBuf packet = new SoundboardSettingsComposer(mode, 60, List.of())
                    .compose()
                    .get();
            packet.skipBytes(6);

            assertEquals(mode.enabled(), packet.readBoolean());
            packet.skipBytes(8);
            assertEquals(mode.wireCode(), packet.readInt());
            assertFalse(packet.isReadable());
        }
    }

    @Test
    void playCarriesAuthoritativeSoundAndActorMetadata() {
        ByteBuf packet = new SoundboardPlayComposer(
                        7, "/sounds/soundboard/campanella.mp3", "campanella", "Campanella", 42, 3, "Simoleo")
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals(7, packet.readInt());
        assertEquals("/sounds/soundboard/campanella.mp3", readString(packet));
        assertEquals("Campanella", readString(packet));
        assertEquals(42, packet.readInt());
        assertEquals(3, packet.readInt());
        assertEquals("Simoleo", readString(packet));
        assertEquals("campanella", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void userSettingsAppendSoundboardVolumeAfterExistingFields() {
        User user = mock(User.class);
        UserStats stats = mock(UserStats.class);
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
        when(user.getUserStats()).thenReturn(stats);

        ByteBuf packet = new MeMenuSettingsComposer(user).compose().get();
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
        // The AIR 13 tail: wired whisper switch, chat mode, bubble width, scroll speed, online indicator.
        assertFalse(packet.readBoolean());
        assertEquals(0, packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(0, packet.readInt());
        // Extended profile visibility, appended after the AIR 13 tail.
        assertTrue(packet.readBoolean());
        // The stored wired menu preferences, empty until saved once.
        assertEquals(0, packet.readShort());
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
        SoundboardSound disabled = new SoundboardSound(7, "Campanella", "bell", "/sounds/bell.mp3", false, 20, 5, 15);
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
        assertEquals("bell", readString(packet));
        // the pad's own cooldown closes the packet, one per pad in the same order
        assertEquals(15, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void aPadCooldownDenialHasItsOwnReasonSoThePanelIsNotLocked() {
        ByteBuf packet = new SoundboardPlayDeniedComposer(SoundboardPlayDeniedComposer.Reason.PAD_COOLDOWN, 8)
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals(5, packet.readInt());
        assertEquals(8, packet.readInt());
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
