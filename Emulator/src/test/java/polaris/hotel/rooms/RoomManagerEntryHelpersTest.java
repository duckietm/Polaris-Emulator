package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserStats;

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

    private static Room room(RoomState state) {
        Room room = mock(Room.class);
        when(room.getState()).thenReturn(state);
        return room;
    }
}
