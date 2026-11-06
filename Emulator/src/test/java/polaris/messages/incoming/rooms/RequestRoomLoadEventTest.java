package polaris.messages.incoming.rooms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.messages.ClientMessage;

class RequestRoomLoadEventTest {

    private GameEnvironment originalEnvironment;
    private RoomManager roomManager;
    private User user;
    private UserInfo info;
    private UserStats stats;

    @BeforeEach
    void install() throws Exception {
        this.roomManager = mock(RoomManager.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(this.roomManager);
        Field field = Emulator.class.getDeclaredField("gameEnvironment");
        field.setAccessible(true);
        this.originalEnvironment = (GameEnvironment) field.get(null);
        field.set(null, environment);

        this.user = mock(User.class);
        this.info = mock(UserInfo.class);
        this.stats = mock(UserStats.class);
        when(this.user.getUserInfo()).thenReturn(this.info);
        when(this.user.getUserStats()).thenReturn(this.stats);
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
        when(this.user.getRoomUnit()).thenReturn(unit);

        this.handle(41);

        verify(current).removeUser(this.user, true);
        verify(this.roomManager).enterRoomAt(this.user, 41, "", 5, 6);
    }

    @Test
    void clientSpawnCoordinatesAreNeverTrusted() throws Exception {
        // A player could otherwise enter on any open tile (past gates, inside game arenas).
        this.handle(41, 7, 8);

        verify(this.roomManager).enterRoomAt(this.user, 41, "", -1, -1);
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
        verify(this.roomManager).enterRoomAt(this.user, 42, "", -1, -1);
    }

    @Test
    void sameRoomAfterTheThrottleWindowPasses() throws Exception {
        this.stats.roomOpenedId = 41;
        this.stats.roomOpenedAtMillis = System.currentTimeMillis() - 2000;

        this.handle(41);

        verify(this.roomManager).enterRoomAt(this.user, 41, "", -1, -1);
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
        when(client.getUser()).thenReturn(this.user);
        RequestRoomLoadEvent event = new RequestRoomLoadEvent();
        event.client = client;
        event.packet = new ClientMessage(2312, buffer);
        event.handle();
    }
}
