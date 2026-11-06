package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;

/** A user placing or moving furni by hand does not block their own tile; anyone else still does. */
class RoomPlaceOnOwnTileTest {

    private static User standingAt(int x, int y) {
        User user = mock(User.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getX()).thenReturn((short) x);
        when(unit.getY()).thenReturn((short) y);
        when(user.getRoomUnit()).thenReturn(unit);
        return user;
    }

    @Test
    void theActorsOwnTileIsFree() {
        Room room = mock(Room.class);
        User owner = standingAt(3, 3);
        when(room.getUsers()).thenReturn(List.of(owner));

        assertFalse(RoomItemPlacementService.hasOtherUsersAt(room, 3, 3, owner));
    }

    @Test
    void anotherAvatarStillBlocks() {
        Room room = mock(Room.class);
        User owner = standingAt(3, 3);
        User visitor = standingAt(3, 3);
        when(room.getUsers()).thenReturn(List.of(owner, visitor));

        assertTrue(RoomItemPlacementService.hasOtherUsersAt(room, 3, 3, owner));
    }

    @Test
    void withoutAnActorEveryAvatarBlocks() {
        Room room = mock(Room.class);
        when(room.hasUsersAt(3, 3)).thenReturn(true);

        assertTrue(RoomItemPlacementService.hasOtherUsersAt(room, 3, 3, null));
    }
}
