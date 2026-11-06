package polaris.messages.incoming.soundboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Rank;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.soundboard.SoundboardCatalogCommand;
import polaris.hotel.soundboard.SoundboardCatalogResult;
import polaris.hotel.soundboard.SoundboardManager;
import polaris.hotel.soundboard.SoundboardRoomMode;
import polaris.hotel.soundboard.SoundboardSound;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.ClientMessage;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.Outgoing;

class SoundboardHandlerBehaviourTest {

    @Test
    void aRefusedToggleChangesNothingAndTellsTheActorWhatIsTrue() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, false);

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardSetEnabledEvent(), packet(9307, 1));
        }

        verify(fixture.room, never()).setSoundboardMode(any());
        verify(fixture.manager, never()).setRoomMode(anyInt(), any());
        assertEquals(List.of(Outgoing.SoundboardSettingsComposer), fixture.headersSentTo(fixture.client));
    }

    @Test
    void theOwnersToggleIsSavedAndEveryoneInTheRoomHearsOfIt() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(fixture.actorId, false);
        User neighbour = fixture.joinRoom(5);

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardSetEnabledEvent(), packet(9307, 1));
        }

        verify(fixture.room).setSoundboardMode(SoundboardRoomMode.EVERYONE);
        verify(fixture.manager).setRoomMode(fixture.roomId, SoundboardRoomMode.EVERYONE);
        assertEquals(List.of(Outgoing.SoundboardSettingsComposer), fixture.headersSentTo(fixture.client));
        verify(neighbour.getClient(), times(1)).sendResponse(any(ServerMessage.class));
    }

    @Test
    void theOwnerCanLimitTheRoomToPeopleWithRights() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(fixture.actorId, false);

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardSetEnabledEvent(), packet(9307, 2));
        }

        verify(fixture.room).setSoundboardMode(SoundboardRoomMode.RIGHTS);
        verify(fixture.manager).setRoomMode(fixture.roomId, SoundboardRoomMode.RIGHTS);
    }

    @Test
    void aCodeNobodyDefinedSwitchesTheSoundboardOff() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(fixture.actorId, true);

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardSetEnabledEvent(), packet(9307, 9));
        }

        verify(fixture.room).setSoundboardMode(SoundboardRoomMode.OFF);
    }

    @Test
    void inTheRightsModeAPlayerWithoutRightsIsDeniedAndNobodyHearsIt() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, SoundboardRoomMode.RIGHTS);
        when(fixture.room.hasRights(fixture.user)).thenReturn(false);

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardPlayEvent(), packet(9306, 3));
        }

        assertEquals(List.of(Outgoing.SoundboardPlayDeniedComposer), fixture.headersSentTo(fixture.client));
        verify(fixture.room, never()).sendComposer(any(ServerMessage.class));
    }

    @Test
    void inTheRightsModeAPlayerWithRightsPlaysNormally() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, SoundboardRoomMode.RIGHTS);
        when(fixture.room.hasRights(fixture.user)).thenReturn(true);
        SoundboardSound bell = new SoundboardSound(3, "Bell", "bell", "", 1);
        when(fixture.manager.tryPlay(anyInt(), anyInt(), anyInt(), anyLong()))
                .thenReturn(new SoundboardManager.PlayDecision(true, bell, SoundboardManager.DenialReason.NONE, 0));

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardPlayEvent(), packet(9306, 3));
        }

        verify(fixture.room, times(1)).sendComposer(any(ServerMessage.class));
    }

    @Test
    void aCatalogChangeReachesTheActiveRoomsWithTheSoundboardOnAndOnlyThose() {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, true);
        Room silentRoom = mock(Room.class);
        User silentListener = fixture.player(99);
        when(silentRoom.isSoundboardEnabled()).thenReturn(false);
        when(silentRoom.getUsers()).thenReturn(List.of(silentListener));
        fixture.activeRooms.add(silentRoom);

        SoundboardSettingsSender.sendToActiveRooms(fixture.manager, fixture.roomManager);

        assertEquals(List.of(Outgoing.SoundboardSettingsComposer), fixture.headersSentTo(fixture.client));
        verify(silentListener.getClient(), never()).sendResponse(any(ServerMessage.class));
    }

    @Test
    void aSuccessfulUpsertPushesTheCatalogToTheRoom() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, true);
        when(fixture.user.hasPermission("acc_soundboard_manage")).thenReturn(true);
        when(fixture.manager.upsert(anyInt(), any(SoundboardCatalogCommand.class)))
                .thenReturn(SoundboardCatalogResult.success(5));

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardCatalogUpsertEvent(), upsertPacket());
        }

        assertEquals(
                List.of(Outgoing.SoundboardCatalogResultComposer, Outgoing.SoundboardSettingsComposer),
                fixture.headersSentTo(fixture.client));
    }

    @Test
    void aFailedUpsertOnlyAnswersTheStaffMember() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, true);
        when(fixture.user.hasPermission("acc_soundboard_manage")).thenReturn(true);
        when(fixture.manager.upsert(anyInt(), any(SoundboardCatalogCommand.class)))
                .thenReturn(SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.INVALID_NAME));

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardCatalogUpsertEvent(), upsertPacket());
        }

        assertEquals(List.of(Outgoing.SoundboardCatalogResultComposer), fixture.headersSentTo(fixture.client));
    }

    @Test
    void aSuccessfulReorderPushesTheCatalogToTheRoom() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, true);
        when(fixture.user.hasPermission("acc_soundboard_manage")).thenReturn(true);
        when(fixture.manager.reorder(anyInt(), any())).thenReturn(SoundboardCatalogResult.success(0));

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardCatalogReorderEvent(), packet(9344, 2, 11, 10));
        }

        assertEquals(
                List.of(Outgoing.SoundboardCatalogResultComposer, Outgoing.SoundboardSettingsComposer),
                fixture.headersSentTo(fixture.client));
    }

    @Test
    void aPadInARoomWithTheSoundboardOffIsDeniedAndNobodyHearsIt() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, false);

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardPlayEvent(), packet(9306, 3));
        }

        assertEquals(List.of(Outgoing.SoundboardPlayDeniedComposer), fixture.headersSentTo(fixture.client));
        verify(fixture.room, never()).sendComposer(any(ServerMessage.class));
    }

    @Test
    void aPadInCooldownIsDeniedToTheCallerOnly() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, true);
        when(fixture.manager.tryPlay(anyInt(), anyInt(), anyInt(), anyLong()))
                .thenReturn(
                        new SoundboardManager.PlayDecision(false, null, SoundboardManager.DenialReason.COOLDOWN, 12));

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardPlayEvent(), packet(9306, 3));
        }

        assertEquals(List.of(Outgoing.SoundboardPlayDeniedComposer), fixture.headersSentTo(fixture.client));
        verify(fixture.room, never()).sendComposer(any(ServerMessage.class));
    }

    @Test
    void anAllowedPadIsBroadcastToTheWholeRoomAndNotAnsweredSeparately() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, true);
        SoundboardSound bell = new SoundboardSound(3, "Bell", "bell", "", 1);
        when(fixture.manager.tryPlay(anyInt(), anyInt(), anyInt(), anyLong()))
                .thenReturn(new SoundboardManager.PlayDecision(true, bell, SoundboardManager.DenialReason.NONE, 0));

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardPlayEvent(), packet(9306, 3));
        }

        ArgumentCaptor<ServerMessage> broadcast = ArgumentCaptor.forClass(ServerMessage.class);
        verify(fixture.room, times(1)).sendComposer(broadcast.capture());
        assertEquals(Outgoing.SoundboardPlayComposer, broadcast.getValue().getHeader());
        assertEquals(List.of(), fixture.headersSentTo(fixture.client));
    }

    private static ServerMessage packet(int header, int... values) {
        ServerMessage packet = new ServerMessage();
        packet.init(header);
        for (int value : values) {
            packet.appendInt(value);
        }
        return packet;
    }

    private static ServerMessage upsertPacket() {
        ServerMessage packet = new ServerMessage();
        packet.init(9343);
        packet.appendInt(0);
        packet.appendString("Bell");
        packet.appendString("");
        packet.appendInt(1);
        packet.appendBoolean(true);
        packet.appendString("bell");
        return packet;
    }

    private static final class Fixture {
        final int actorId = 42;
        final int roomId = 900;
        final GameClient client = mock(GameClient.class);
        final User user = mock(User.class);
        final UserInfo info = mock(UserInfo.class);
        final Rank rank = mock(Rank.class);
        final RoomUnit roomUnit = mock(RoomUnit.class);
        final Room room = mock(Room.class);
        final SoundboardManager manager = mock(SoundboardManager.class);
        final GameEnvironment environment = mock(GameEnvironment.class);
        final RoomManager roomManager = mock(RoomManager.class);
        final ArrayList<Room> activeRooms = new ArrayList<>();
        final List<User> inRoom = new ArrayList<>();

        Fixture() {
            when(this.client.getUser()).thenReturn(this.user);
            when(this.user.getClient()).thenReturn(this.client);
            when(this.user.getUserInfo()).thenReturn(this.info);
            when(this.user.getRoomUnit()).thenReturn(this.roomUnit);
            when(this.info.getId()).thenReturn(this.actorId);
            when(this.info.getUsername()).thenReturn("actor");
            when(this.info.getRank()).thenReturn(this.rank);
            when(this.info.getCurrentRoom()).thenReturn(this.room);
            when(this.rank.getId()).thenReturn(3);
            when(this.roomUnit.getId()).thenReturn(1);
            when(this.manager.getSoundsForRank(anyInt())).thenReturn(List.of());
            when(this.manager.getCooldownSecondsForRank(anyInt())).thenReturn(60);
            when(this.environment.getSoundboardManager()).thenReturn(this.manager);
            when(this.environment.getRoomManager()).thenReturn(this.roomManager);
            when(this.roomManager.getActiveRooms()).thenReturn(this.activeRooms);
        }

        void inRoomOwnedBy(int ownerId, boolean soundboardOn) {
            this.inRoomOwnedBy(ownerId, soundboardOn ? SoundboardRoomMode.EVERYONE : SoundboardRoomMode.OFF);
        }

        void inRoomOwnedBy(int ownerId, SoundboardRoomMode mode) {
            when(this.room.getId()).thenReturn(this.roomId);
            when(this.room.getOwnerId()).thenReturn(ownerId);
            when(this.room.getSoundboardMode()).thenReturn(mode);
            when(this.room.isSoundboardEnabled()).thenReturn(mode.enabled());
            when(this.room.getUsers()).thenAnswer(invocation -> List.copyOf(this.inRoom));
            this.inRoom.add(this.user);
            this.activeRooms.add(this.room);
        }

        User joinRoom(int userId) {
            User other = this.player(userId);
            this.inRoom.add(other);
            return other;
        }

        User player(int userId) {
            User other = mock(User.class);
            UserInfo otherInfo = mock(UserInfo.class);
            Rank otherRank = mock(Rank.class);
            when(other.getClient()).thenReturn(mock(GameClient.class));
            when(other.getUserInfo()).thenReturn(otherInfo);
            when(otherInfo.getId()).thenReturn(userId);
            when(otherInfo.getRank()).thenReturn(otherRank);
            when(otherRank.getId()).thenReturn(1);
            return other;
        }

        MockedStatic<Emulator> serving() {
            MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
            emulator.when(Emulator::getGameEnvironment).thenReturn(this.environment);
            return emulator;
        }

        List<Integer> headersSentTo(GameClient target) {
            ArgumentCaptor<ServerMessage> sent = ArgumentCaptor.forClass(ServerMessage.class);
            verify(target, atLeast(0)).sendResponse(sent.capture());
            return sent.getAllValues().stream().map(ServerMessage::getHeader).toList();
        }

        void handle(MessageHandler handler, ServerMessage packet) throws Exception {
            ByteBuf buffer = packet.get();
            try {
                buffer.skipBytes(6);
                handler.client = this.client;
                handler.packet = new ClientMessage(packet.getHeader(), buffer);
                handler.handle();
            } finally {
                buffer.release();
            }
        }
    }
}
