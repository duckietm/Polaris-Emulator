package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.hotel.users.UserStats;
import polaris.messages.outgoing.hotelview.HotelViewComposer;

class RoomManagerEntryHelpersTest {

    @Test
    void hostingMinutesAreComputedFromUnixSeconds() {
        assertEquals(0, RoomManager.minutesInRoom(1_000, 1_059));
        assertEquals(1, RoomManager.minutesInRoom(1_000, 1_060));
        assertEquals(90, RoomManager.minutesInRoom(1_000, 1_000 + 90 * 60));
    }

    @Test
    void unknownOrFutureEntryGivesNoHostingMinutes() {
        assertEquals(0, RoomManager.minutesInRoom(0, 1_790_000_000));
        assertEquals(0, RoomManager.minutesInRoom(2_000, 1_000));
    }

    @Test
    void roomOpenStampsTheRoomSecondsAndMillis() {
        User user = mock(User.class);
        UserStats stats = mock(UserStats.class);
        when(user.getUserStats()).thenReturn(stats);
        long before = System.currentTimeMillis();

        Room room = mock(Room.class);
        when(room.getId()).thenReturn(41);

        new RoomManager(false).markRoomOpened(user, room);

        assertEquals(41, stats.roomOpenedId);
        assertTrue(stats.roomOpenedAtMillis >= before);
        assertTrue(stats.roomEnterTimestamp > 0);
        assertTrue(stats.roomEnterTimestamp < stats.roomOpenedAtMillis / 100);
    }

    @Test
    void roomInfoPrefetchOnlyForRoomsTheUserCanWalkInto() {
        User visitor = mock(User.class);

        assertTrue(RoomManager.mayPrefetchRoomData(visitor, room(RoomState.OPEN)));
        assertFalse(RoomManager.mayPrefetchRoomData(visitor, room(RoomState.LOCKED)));
        assertFalse(RoomManager.mayPrefetchRoomData(visitor, room(RoomState.PASSWORD)));
        assertFalse(RoomManager.mayPrefetchRoomData(visitor, room(RoomState.INVISIBLE)));

        Room banned = room(RoomState.OPEN);
        when(banned.isBanned(visitor)).thenReturn(true);
        assertFalse(RoomManager.mayPrefetchRoomData(visitor, banned));

        Room trialLocked = room(RoomState.OPEN);
        when(trialLocked.isBuildersClubTrialLocked()).thenReturn(true);
        assertFalse(RoomManager.mayPrefetchRoomData(visitor, trialLocked));

        Room owned = room(RoomState.PASSWORD);
        User owner = mock(User.class);
        when(owned.isOwner(owner)).thenReturn(true);
        assertTrue(RoomManager.mayPrefetchRoomData(owner, owned));

        User staff = mock(User.class);
        when(staff.hasPermission(Permission.ACC_ENTERANYROOM)).thenReturn(true);
        assertTrue(RoomManager.mayPrefetchRoomData(staff, room(RoomState.LOCKED)));
    }

    @Test
    void aRoomThatFilledUpBeforeTheHeightmapQueuesTheVisitor() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(4141);
        when(room.getUserCount()).thenReturn(10);
        when(room.getUsersMax()).thenReturn(10);
        User visitor = visitorLoading(4141);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getUserManager()).thenReturn(mock(UserManager.class));
        Field field = Emulator.class.getDeclaredField("gameEnvironment");
        field.setAccessible(true);
        Object original = field.get(null);
        field.set(null, environment);
        try {
            // The capacity was only checked when the room was opened: everyone who opened it while one
            // place was free entered a round trip later, past usersMax and past the queue.
            new RoomManager(false).enterRoom(visitor, room);
        } finally {
            RoomVisitorQueueSupport.remove(room, visitor);
            field.set(null, original);
        }

        verify(room, never()).addUser(visitor);
        verify(visitor.getUserInfo()).setLoadingRoom(0);
        verify(visitor.getClient()).sendResponse(any(HotelViewComposer.class));
    }

    @Test
    void staffAndRightsHoldersEnterAFullRoom() {
        Room room = mock(Room.class);
        when(room.getUserCount()).thenReturn(10);
        when(room.getUsersMax()).thenReturn(10);
        User staff = visitorLoading(4142);
        when(staff.hasPermission(Permission.ACC_FULLROOMS)).thenReturn(true);
        User rights = visitorLoading(4142);
        when(room.hasRights(rights)).thenReturn(true);

        assertFalse(new RoomManager(false).queuedBecauseFull(staff, room));
        assertFalse(new RoomManager(false).queuedBecauseFull(rights, room));
    }

    private static User visitorLoading(int roomId) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(7);
        when(info.getLoadingRoom()).thenReturn(roomId);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getClient()).thenReturn(mock(GameClient.class));
        return user;
    }

    private static Room room(RoomState state) {
        Room room = mock(Room.class);
        when(room.getState()).thenReturn(state);
        return room;
    }
}
