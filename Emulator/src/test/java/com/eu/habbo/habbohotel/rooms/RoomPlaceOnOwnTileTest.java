package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A user placing or moving furni by hand does not block their own tile; anyone else still does. */
class RoomPlaceOnOwnTileTest {

    private static Habbo standingAt(int x, int y) {
        Habbo habbo = mock(Habbo.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getX()).thenReturn((short) x);
        when(unit.getY()).thenReturn((short) y);
        when(habbo.getRoomUnit()).thenReturn(unit);
        return habbo;
    }

    @Test
    void theActorsOwnTileIsFree() {
        Room room = mock(Room.class);
        Habbo owner = standingAt(3, 3);
        when(room.getHabbos()).thenReturn(List.of(owner));

        assertFalse(RoomItemPlacementService.hasOtherHabbosAt(room, 3, 3, owner));
    }

    @Test
    void anotherAvatarStillBlocks() {
        Room room = mock(Room.class);
        Habbo owner = standingAt(3, 3);
        Habbo visitor = standingAt(3, 3);
        when(room.getHabbos()).thenReturn(List.of(owner, visitor));

        assertTrue(RoomItemPlacementService.hasOtherHabbosAt(room, 3, 3, owner));
    }

    @Test
    void withoutAnActorEveryAvatarBlocks() {
        Room room = mock(Room.class);
        when(room.hasHabbosAt(3, 3)).thenReturn(true);

        assertTrue(RoomItemPlacementService.hasOtherHabbosAt(room, 3, 3, null));
    }
}
