package com.eu.habbo.messages.incoming.soundboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.soundboard.SoundboardCatalogCommand;
import com.eu.habbo.habbohotel.soundboard.SoundboardCatalogResult;
import com.eu.habbo.habbohotel.soundboard.SoundboardManager;
import com.eu.habbo.habbohotel.soundboard.SoundboardSound;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class SoundboardHandlerBehaviourTest {

    @Test
    void aRefusedToggleChangesNothingAndTellsTheActorWhatIsTrue() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, false);

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardSetEnabledEvent(), packet(9307, 1));
        }

        verify(fixture.room, never()).setSoundboardEnabled(true);
        verify(fixture.manager, never()).setRoomEnabled(anyInt(), anyBoolean());
        assertEquals(List.of(Outgoing.SoundboardSettingsComposer), fixture.headersSentTo(fixture.client));
    }

    @Test
    void theOwnersToggleIsSavedAndEveryoneInTheRoomHearsOfIt() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(fixture.actorId, false);
        Habbo neighbour = fixture.joinRoom(5);

        try (MockedStatic<Emulator> emulator = fixture.serving()) {
            fixture.handle(new SoundboardSetEnabledEvent(), packet(9307, 1));
        }

        verify(fixture.room).setSoundboardEnabled(true);
        verify(fixture.manager).setRoomEnabled(fixture.roomId, true);
        assertEquals(List.of(Outgoing.SoundboardSettingsComposer), fixture.headersSentTo(fixture.client));
        verify(neighbour.getClient(), times(1)).sendResponse(any(ServerMessage.class));
    }

    @Test
    void aCatalogChangeReachesTheActiveRoomsWithTheSoundboardOnAndOnlyThose() {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, true);
        Room silentRoom = mock(Room.class);
        Habbo silentListener = fixture.player(99);
        when(silentRoom.isSoundboardEnabled()).thenReturn(false);
        when(silentRoom.getHabbos()).thenReturn(List.of(silentListener));
        fixture.activeRooms.add(silentRoom);

        SoundboardSettingsSender.sendToActiveRooms(fixture.manager, fixture.roomManager);

        assertEquals(List.of(Outgoing.SoundboardSettingsComposer), fixture.headersSentTo(fixture.client));
        verify(silentListener.getClient(), never()).sendResponse(any(ServerMessage.class));
    }

    @Test
    void aSuccessfulUpsertPushesTheCatalogToTheRoom() throws Exception {
        Fixture fixture = new Fixture();
        fixture.inRoomOwnedBy(7, true);
        when(fixture.habbo.hasPermission("acc_soundboard_manage")).thenReturn(true);
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
        when(fixture.habbo.hasPermission("acc_soundboard_manage")).thenReturn(true);
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
        when(fixture.habbo.hasPermission("acc_soundboard_manage")).thenReturn(true);
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
        final Habbo habbo = mock(Habbo.class);
        final HabboInfo info = mock(HabboInfo.class);
        final Rank rank = mock(Rank.class);
        final RoomUnit roomUnit = mock(RoomUnit.class);
        final Room room = mock(Room.class);
        final SoundboardManager manager = mock(SoundboardManager.class);
        final GameEnvironment environment = mock(GameEnvironment.class);
        final RoomManager roomManager = mock(RoomManager.class);
        final ArrayList<Room> activeRooms = new ArrayList<>();
        final List<Habbo> inRoom = new ArrayList<>();

        Fixture() {
            when(this.client.getHabbo()).thenReturn(this.habbo);
            when(this.habbo.getClient()).thenReturn(this.client);
            when(this.habbo.getHabboInfo()).thenReturn(this.info);
            when(this.habbo.getRoomUnit()).thenReturn(this.roomUnit);
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
            when(this.room.getId()).thenReturn(this.roomId);
            when(this.room.getOwnerId()).thenReturn(ownerId);
            when(this.room.isSoundboardEnabled()).thenReturn(soundboardOn);
            when(this.room.getHabbos()).thenAnswer(invocation -> List.copyOf(this.inRoom));
            this.inRoom.add(this.habbo);
            this.activeRooms.add(this.room);
        }

        Habbo joinRoom(int userId) {
            Habbo other = this.player(userId);
            this.inRoom.add(other);
            return other;
        }

        Habbo player(int userId) {
            Habbo other = mock(Habbo.class);
            HabboInfo otherInfo = mock(HabboInfo.class);
            Rank otherRank = mock(Rank.class);
            when(other.getClient()).thenReturn(mock(GameClient.class));
            when(other.getHabboInfo()).thenReturn(otherInfo);
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
