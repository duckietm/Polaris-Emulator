package com.eu.habbo.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionGift;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A wrapped gift is unwrapped a second after the click. Picked up in that second, its contents were
 * still delivered and the wrapper stayed in the inventory too; an item that no longer loaded made the
 * whole gift fail.
 */
class OpenGiftStillInRoomTest {

    @Test
    void aGiftNoLongerInTheRoomIsNotOpened() {
        InteractionGift gift = mock(InteractionGift.class);
        when(gift.getId()).thenReturn(42);
        Room room = mock(Room.class);
        when(room.getHabboItem(42)).thenReturn(null);

        new OpenGift(gift, mock(Habbo.class), room).run();

        verify(gift, never()).loadItems();
    }

    @Test
    void aGiftBeingOpenedCannotBePickedUpAndMissingItemsAreSkipped() throws Exception {
        String ownership =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomItemOwnershipService.java"));
        String open = Files.readString(Path.of("src/main/java/com/eu/habbo/threading/runnables/OpenGift.java"));

        assertTrue(ownership.contains("if (item instanceof InteractionGift gift && gift.isOpening()) {"));
        assertTrue(open.contains("items.removeIf(Objects::isNull);"));
    }
}
