package polaris.messages.incoming.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomRightsManager;

/** Giving up rights needs the room you are in, rights held there, and not being its owner. */
class RoomRemoveRightsEventTest {

    private static Room room(int id, int ownerId, int... rightHolders) {
        Room room = mock(Room.class);
        RoomRightsManager rights = mock(RoomRightsManager.class);
        IntSet holders = new IntOpenHashSet(rightHolders);
        when(room.getId()).thenReturn(id);
        when(room.getOwnerId()).thenReturn(ownerId);
        when(room.getRightsManager()).thenReturn(rights);
        when(rights.getRights()).thenReturn(holders);
        return room;
    }

    @Test
    void aRightsHolderInTheRoomCanGiveThemUp() {
        assertTrue(RoomRemoveRightsEvent.canGiveUpRights(room(5, 1, 9), 5, 9));
    }

    @Test
    void everyOtherCallerIsIgnored() {
        assertFalse(RoomRemoveRightsEvent.canGiveUpRights(null, 5, 9), "not in a room");
        assertFalse(RoomRemoveRightsEvent.canGiveUpRights(room(5, 1, 9), 6, 9), "another room id");
        assertFalse(RoomRemoveRightsEvent.canGiveUpRights(room(5, 1), 5, 9), "no rights held");
        assertFalse(RoomRemoveRightsEvent.canGiveUpRights(room(5, 9, 9), 5, 9), "the owner");
    }
}
