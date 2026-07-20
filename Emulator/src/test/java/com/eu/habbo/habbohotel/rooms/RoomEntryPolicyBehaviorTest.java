package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericErrorMessagesComposer;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomEnterErrorComposer;
import com.eu.habbo.plugin.PluginManager;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomEntryPolicyBehaviorTest {

    private PluginManager originalPlugins;

    @BeforeEach
    void installPlugins() throws Exception {
        Field field = Emulator.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        this.originalPlugins = (PluginManager) field.get(null);
        PluginManager plugins = mock(PluginManager.class);
        doAnswer(invocation -> invocation.getArgument(0)).when(plugins).fireEvent(any());
        field.set(null, plugins);
    }

    @AfterEach
    void restorePlugins() throws Exception {
        Field field = Emulator.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        field.set(null, this.originalPlugins);
    }

    @Test
    void bannedUserIsRejectedBeforeRoomOpening() {
        RecordingRoomManager manager = new RecordingRoomManager();
        Room room = room(41, RoomState.OPEN);
        Habbo habbo = habbo(41);
        when(room.isBanned(habbo)).thenReturn(true);
        manager.registerActiveRoom(room);

        manager.enterRoom(habbo, 41, "");

        verify(habbo.getClient()).sendResponse(any(RoomEnterErrorComposer.class));
        assertSame(null, manager.openedRoom);
    }

    @Test
    void wrongPasswordReturnsTheEstablishedErrorAndHotelView() {
        RecordingRoomManager manager = new RecordingRoomManager();
        Room room = room(41, RoomState.PASSWORD);
        Habbo habbo = habbo(41);
        when(room.getPassword()).thenReturn("secret");
        manager.registerActiveRoom(room);

        manager.enterRoom(habbo, 41, "wrong");

        verify(habbo.getClient()).sendResponse(any(GenericErrorMessagesComposer.class));
        verify(habbo.getClient()).sendResponse(any(HotelViewComposer.class));
        assertSame(null, manager.openedRoom);
    }

    @Test
    void openRoomPolicyPreservesTheRequestedSpawnInputs() {
        RecordingRoomManager manager = new RecordingRoomManager();
        Room room = room(41, RoomState.OPEN);
        Habbo habbo = habbo(41);
        RoomTile door = mock(RoomTile.class);
        manager.registerActiveRoom(room);

        manager.enterRoom(habbo, 41, "", false, door, true);

        assertSame(room, manager.openedRoom);
        assertSame(door, manager.openedDoor);
    }

    private static Room room(int id, RoomState state) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.getState()).thenReturn(state);
        return room;
    }

    private static Habbo habbo(int queuedRoomId) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        GameClient client = mock(GameClient.class);
        when(info.getRoomQueueId()).thenReturn(queuedRoomId);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getClient()).thenReturn(client);
        return habbo;
    }

    private static final class RecordingRoomManager extends RoomManager {
        private Room openedRoom;
        private RoomTile openedDoor;

        private RecordingRoomManager() {
            super(false);
        }

        @Override
        void openRoom(Habbo habbo, Room room, RoomTile doorLocation, boolean isReconnectSpawn) {
            this.openedRoom = room;
            this.openedDoor = doorLocation;
        }
    }
}
