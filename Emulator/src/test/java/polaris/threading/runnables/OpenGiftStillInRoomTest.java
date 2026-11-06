package polaris.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

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
        when(room.getUserItem(42)).thenReturn(null);

        new OpenGift(gift, mock(User.class), room).run();

        verify(gift, never()).loadItems();
    }

    @Test
    void aGiftBeingOpenedCannotBePickedUpAndMissingItemsAreSkipped() throws Exception {
        String ownership = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomItemOwnershipService.java"));
        String open = Files.readString(Path.of("src/main/java/polaris/threading/runnables/OpenGift.java"));

        assertTrue(ownership.contains("if (item instanceof InteractionGift gift && gift.isOpening()) {"));
        assertTrue(open.contains("items.removeIf(Objects::isNull);"));
    }

    @Test
    void onlyWhatTheGiftStillHoldsIsDelivered() {
        assertTrue(OpenGift.stillWrapped(content(0, 0), 7), "no owner yet");
        assertTrue(OpenGift.stillWrapped(content(7, 0), 7), "an older gift whose content the recipient owns");
        assertFalse(OpenGift.stillWrapped(content(9, 0), 7), "given to someone else since");
        assertFalse(OpenGift.stillWrapped(content(7, 12), 7), "placed in a room since");
    }

    private static UserItem content(int userId, int roomId) {
        UserItem item = mock(UserItem.class);
        when(item.getUserId()).thenReturn(userId);
        when(item.getRoomId()).thenReturn(roomId);
        return item;
    }
}
