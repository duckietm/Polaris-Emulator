package com.eu.habbo.threading.runnables.teleport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A teleport left its room and marked the user in the target room before the entry was decided:
 * banned there, or the room full, they ended up in neither room while the server counted them in the
 * target one.
 */
class TeleportEntryRefusalTest {

    private static Room room(boolean banned, int users, int max, boolean rights, Habbo habbo) {
        Room room = mock(Room.class);
        when(room.isBanned(habbo)).thenReturn(banned);
        when(room.getUserCount()).thenReturn(users);
        when(room.getUsersMax()).thenReturn(max);
        when(room.hasRights(habbo)).thenReturn(rights);
        return room;
    }

    @Test
    void aBannedUserCannotTeleportIn() {
        Habbo habbo = mock(Habbo.class);

        assertFalse(TeleportActionThree.mayEnter(habbo, room(true, 1, 25, false, habbo)));
    }

    @Test
    void aFullRoomTurnsAVisitorAway() {
        Habbo habbo = mock(Habbo.class);

        assertFalse(TeleportActionThree.mayEnter(habbo, room(false, 25, 25, false, habbo)));
        assertTrue(TeleportActionThree.mayEnter(habbo, room(false, 25, 25, true, habbo)), "rights holders still enter");
    }

    @Test
    void staffKeepTheirDoorPermissions() {
        Habbo staff = mock(Habbo.class);
        when(staff.hasPermission(Permission.ACC_ENTERANYROOM)).thenReturn(true);
        when(staff.hasPermission(Permission.ACC_FULLROOMS)).thenReturn(true);

        assertTrue(TeleportActionThree.mayEnter(staff, room(true, 25, 25, false, staff)));
    }

    @Test
    void anOpenRoomLetsTheUserIn() {
        Habbo habbo = mock(Habbo.class);

        assertTrue(TeleportActionThree.mayEnter(habbo, room(false, 3, 25, false, habbo)));
    }

    @Test
    void theTeleportChecksBeforeLeavingTheRoom() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/threading/runnables/teleport/TeleportActionThree.java"));

        int check = source.indexOf("!mayEnter(this.habbo, targetRoom)");
        int leave = source.indexOf("this.room.removeHabbo(this.habbo, false);");

        assertTrue(check > 0 && check < leave, "the entry is decided before the user leaves the room");
    }
}
