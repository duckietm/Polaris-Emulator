package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/**
 * A reload or a floor plan save disposed the room while it was loading, and the load carried on: it started the
 * jukebox and restored the variables, then failed at publishing and left what it had registered in the room.
 */
class RoomAbandonedLoadTest {

    @Test
    void aLoadWhoseRoomWasDisposedStopsAndDropsWhatItRegistered() {
        Room room = mock(Room.class);
        RoomItemManager items = mock(RoomItemManager.class);
        RoomUnitManager units = mock(RoomUnitManager.class);
        when(room.getItemManager()).thenReturn(items);
        when(room.getUnitManager()).thenReturn(units);
        // The load was generation 4; the dispose moved the room on to 5.
        when(room.getLifecycleGeneration()).thenReturn(5L);

        boolean published = new RoomLoadOperations(room, null).finish(4L);

        assertFalse(published);
        verify(room, never()).replaceTraxManager(any());
        verify(items).clear();
        verify(units).clearBots();
        verify(units).clearPets();
        verify(room).failLoadTransition(4L);
    }
}
