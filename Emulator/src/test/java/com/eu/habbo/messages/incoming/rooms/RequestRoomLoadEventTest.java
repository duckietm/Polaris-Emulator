package com.eu.habbo.messages.incoming.rooms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestRoomLoadEventTest {

    private GameEnvironment originalEnvironment;
    private RoomManager roomManager;
    private Habbo habbo;
    private HabboInfo info;
    private HabboStats stats;

    @BeforeEach
    void install() throws Exception {
        this.roomManager = mock(RoomManager.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(this.roomManager);
        Field field = Emulator.class.getDeclaredField("gameEnvironment");
        field.setAccessible(true);
        this.originalEnvironment = (GameEnvironment) field.get(null);
        field.set(null, environment);

        this.habbo = mock(Habbo.class);
        this.info = mock(HabboInfo.class);
        this.stats = mock(HabboStats.class);
        when(this.habbo.getHabboInfo()).thenReturn(this.info);
        when(this.habbo.getHabboStats()).thenReturn(this.stats);
    }

    @AfterEach
    void restore() throws Exception {
        Field field = Emulator.class.getDeclaredField("gameEnvironment");
        field.setAccessible(true);
        field.set(null, this.originalEnvironment);
    }

    @Test
    void reEnteringTheCurrentRoomHandsTheCurrentPositionToTheEntry() throws Exception {
        Room current = mock(Room.class);
        when(current.getId()).thenReturn(41);
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getCurrentLocation())
                .thenReturn(new RoomTile((short) 5, (short) 6, (short) 0, RoomTileState.OPEN, true));
        when(this.info.getCurrentRoom()).thenReturn(current);
        when(this.habbo.getRoomUnit()).thenReturn(unit);

        this.handle(41);

        verify(current).removeHabbo(this.habbo, true);
        verify(this.roomManager).enterRoomAt(this.habbo, 41, "", 5, 6);
    }

    @Test
    void clientSpawnCoordinatesAreHandedToTheEntryUnresolved() throws Exception {
        this.handle(41, 7, 8);

        verify(this.roomManager).enterRoomAt(this.habbo, 41, "", 7, 8);
        verify(this.roomManager, never()).getRoom(anyInt());
        verify(this.roomManager, never()).loadRoom(anyInt());
    }

    @Test
    void doubleClickOnTheRoomJustOpenedIsDropped() throws Exception {
        this.stats.roomOpenedId = 41;
        this.stats.roomOpenedAtMillis = System.currentTimeMillis();
        when(this.info.getLoadingRoom()).thenReturn(41);

        this.handle(41);

        verify(this.roomManager, never()).enterRoomAt(any(), anyInt(), any(), anyInt(), anyInt());
    }

    @Test
    void anotherRoomRightAfterAnOpenAlwaysPasses() throws Exception {
        this.stats.roomOpenedId = 41;
        this.stats.roomOpenedAtMillis = System.currentTimeMillis();
        when(this.info.getLoadingRoom()).thenReturn(41);

        this.handle(42);

        // The still-loading room no longer blocks the entry into the new one.
        verify(this.info).setLoadingRoom(0);
        verify(this.roomManager).enterRoomAt(this.habbo, 42, "", -1, -1);
    }

    @Test
    void sameRoomAfterTheThrottleWindowPasses() throws Exception {
        this.stats.roomOpenedId = 41;
        this.stats.roomOpenedAtMillis = System.currentTimeMillis() - 2000;

        this.handle(41);

        verify(this.roomManager).enterRoomAt(this.habbo, 41, "", -1, -1);
        verify(this.info, never()).setLoadingRoom(anyInt());
    }

    private void handle(int roomId) throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(roomId);
        buffer.writeShort(0);
        this.handle(buffer);
    }

    private void handle(int roomId, int spawnX, int spawnY) throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(roomId);
        buffer.writeShort(0);
        buffer.writeInt(spawnX);
        buffer.writeInt(spawnY);
        this.handle(buffer);
    }

    private void handle(ByteBuf buffer) throws Exception {
        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(this.habbo);
        RequestRoomLoadEvent event = new RequestRoomLoadEvent();
        event.client = client;
        event.packet = new ClientMessage(2312, buffer);
        event.handle();
    }
}
