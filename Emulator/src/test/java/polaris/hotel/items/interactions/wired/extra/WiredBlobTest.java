package polaris.hotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;

/**
 * A blob whose base item has no customparams row, or a click that arrives without the toggle
 * marker, used to throw instead of doing nothing. Both come from data the box does not control.
 */
class WiredBlobTest {

    @Test
    void aBaseItemWithoutCustomParamsStillConstructs() {
        Item base = mock(Item.class);
        when(base.getCustomParams()).thenReturn(null);

        assertDoesNotThrow(() -> new WiredBlob(1, 1, base, "1", 0, 0));
    }

    @Test
    void aClickWithoutTheToggleMarkerIsIgnored() throws Exception {
        Item base = mock(Item.class);
        when(base.getCustomParams()).thenReturn("10,false");
        WiredBlob blob = new WiredBlob(1, 1, base, "1", 0, 0);
        Room room = mock(Room.class);

        assertDoesNotThrow(() -> blob.onClick(null, room, new Object[] {null, null}));

        verify(room, never()).updateItem(blob);
    }
}
