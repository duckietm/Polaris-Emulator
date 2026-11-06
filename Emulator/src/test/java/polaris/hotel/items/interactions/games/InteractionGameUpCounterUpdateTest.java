package polaris.hotel.items.interactions.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;

/** A counter change goes out as an ObjectDataUpdate with our "now TAB base" data, not as a full ObjectUpdate. */
class InteractionGameUpCounterUpdateTest {

    @Test
    void anAdjustSendsTheDataOnly() {
        Room room = mock(Room.class);
        InteractionGameUpCounter counter = new InteractionGameUpCounter(1, 1, mock(Item.class), "0", 0, 0);

        try (MockedStatic<WiredManager> ignored = mockStatic(WiredManager.class)) {
            counter.adjustCounter(room, 2, 1, 0);
        }

        verify(room).sendComposer(any(ServerMessage.class));
        verify(room, never()).updateItem(any(UserItem.class));
        assertEquals("60", counter.getExtradata().split("\t")[0]);
    }
}
