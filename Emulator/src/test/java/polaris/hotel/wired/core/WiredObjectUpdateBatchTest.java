package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

/**
 * A furni a wired firing moves several times without animation gets one object update when the firing ends, with
 * where it ended up; outside a firing the update is the caller's to send at once.
 */
class WiredObjectUpdateBatchTest {
    private static UserItem item(int id, int roomId) {
        UserItem item = mock(UserItem.class, RETURNS_DEEP_STUBS);
        when(item.getId()).thenReturn(id);
        when(item.getRoomId()).thenReturn(roomId);
        return item;
    }

    @Test
    void outsideAFiringTheUpdateIsNotHeld() {
        assertFalse(WiredMoveCarryHelper.deferObjectUpdate(mock(Room.class), item(1, 5)));
    }

    @Test
    void aFiringSendsOneUpdatePerFurniWhenItEnds() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(5);
        UserItem moved = item(1, 5);
        UserItem pickedUp = item(2, 0);

        WiredMoveCarryHelper.beginMovementCollection();
        try {
            // A nested firing shares the outer one's batch.
            WiredMoveCarryHelper.beginMovementCollection();
            assertTrue(WiredMoveCarryHelper.deferObjectUpdate(room, moved));
            WiredMoveCarryHelper.finishMovementCollection();

            assertTrue(WiredMoveCarryHelper.deferObjectUpdate(room, moved));
            assertTrue(WiredMoveCarryHelper.deferObjectUpdate(room, pickedUp));
            verify(room, never()).sendComposer(any(ServerMessage.class));
        } finally {
            WiredMoveCarryHelper.finishMovementCollection();
        }

        // One update for the furni moved twice; none for the one that is no longer in the room.
        verify(room, times(1)).sendComposer(any());
        assertFalse(WiredMoveCarryHelper.deferObjectUpdate(room, moved));
    }

    @Test
    void theRoomHandsItsMoveUpdatesToTheBatch() throws Exception {
        String service = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomItemMovementService.java"));

        assertTrue(service.contains("if (sendUpdates && !WiredMoveCarryHelper.deferObjectUpdate(this.room, item)) {"));
    }
}
