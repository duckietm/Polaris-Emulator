package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * At shutdown a room bundle's model room (kept loaded on purpose) returned from dispose at once, and so did a room
 * whose unloading a plugin cancelled: neither saved its pending items.
 */
class RoomShutdownDisposeTest {

    @Test
    void aLockedRoomIsUnlockedBeforeItIsDisposed() {
        Room room = mock(Room.class);
        room.preventUnloading = true;
        room.preventUncaching = true;

        RoomLifecycleService.disposeForShutdown(room);

        assertFalse(room.preventUnloading);
        assertFalse(room.preventUncaching);
        verify(room).dispose();
    }

    @Test
    void aRoomAPluginKeepsLoadedStillSavesItsItems() {
        Room room = mock(Room.class);
        RoomItemManager items = mock(RoomItemManager.class);
        when(room.getItemManager()).thenReturn(items);
        when(room.isLoaded()).thenReturn(true);

        RoomLifecycleService.disposeForShutdown(room);

        InOrder order = inOrder(room, items);
        order.verify(room).dispose();
        order.verify(items).saveAllPendingItems();
    }

    @Test
    void anUnloadedRoomIsNotSavedTwice() {
        Room room = mock(Room.class);
        RoomItemManager items = mock(RoomItemManager.class);
        when(room.getItemManager()).thenReturn(items);

        RoomLifecycleService.disposeForShutdown(room);

        verify(items, never()).saveAllPendingItems();
    }
}
