package polaris.threading.runnables.teleport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

/**
 * A teleport left its room and marked the user in the target room before the entry was decided:
 * banned there, or the room full, they ended up in neither room while the server counted them in the
 * target one.
 */
class TeleportEntryRefusalTest {

    private static Room room(boolean banned, int users, int max, boolean rights, User user) {
        Room room = mock(Room.class);
        when(room.isBanned(user)).thenReturn(banned);
        when(room.getUserCount()).thenReturn(users);
        when(room.getUsersMax()).thenReturn(max);
        when(room.hasRights(user)).thenReturn(rights);
        return room;
    }

    @Test
    void aBannedUserCannotTeleportIn() {
        User user = mock(User.class);

        assertFalse(TeleportActionThree.mayEnter(user, room(true, 1, 25, false, user)));
    }

    @Test
    void aFullRoomTurnsAVisitorAway() {
        User user = mock(User.class);

        assertFalse(TeleportActionThree.mayEnter(user, room(false, 25, 25, false, user)));
        assertTrue(TeleportActionThree.mayEnter(user, room(false, 25, 25, true, user)), "rights holders still enter");
    }

    @Test
    void staffKeepTheirDoorPermissions() {
        User staff = mock(User.class);
        when(staff.hasPermission(Permission.ACC_ENTERANYROOM)).thenReturn(true);
        when(staff.hasPermission(Permission.ACC_FULLROOMS)).thenReturn(true);

        assertTrue(TeleportActionThree.mayEnter(staff, room(true, 25, 25, false, staff)));
    }

    @Test
    void anOpenRoomLetsTheUserIn() {
        User user = mock(User.class);

        assertTrue(TeleportActionThree.mayEnter(user, room(false, 3, 25, false, user)));
    }

    @Test
    void theTeleportChecksBeforeLeavingTheRoom() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/polaris/threading/runnables/teleport/TeleportActionThree.java"));

        int check = source.indexOf("!mayEnter(this.user, targetRoom)");
        int leave = source.indexOf("this.room.removeUser(this.user, false);");

        assertTrue(check > 0 && check < leave, "the entry is decided before the user leaves the room");
    }
}
