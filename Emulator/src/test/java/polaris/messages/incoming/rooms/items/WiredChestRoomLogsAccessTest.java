package polaris.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.chest.ChestStorage;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;

/** Who without room rights may read the paged log of one chest. */
class WiredChestRoomLogsAccessTest {
    private static Room roomWith(int id, UserItem item) {
        Room room = mock(Room.class);
        when(room.getUserItem(id)).thenReturn(item);
        return room;
    }

    private static InteractionWiredChest chest(boolean open) {
        ChestStorage contents = mock(ChestStorage.class);
        when(contents.isAccessOpen()).thenReturn(open);
        InteractionWiredChest chest = mock(InteractionWiredChest.class);
        when(chest.getContents()).thenReturn(contents);
        return chest;
    }

    @Test
    void anOpenChestShowsItsLogToAnyone() {
        assertTrue(WiredChestRoomLogsEvent.mayReadChestLog(roomWith(7, chest(true)), 7));
    }

    @Test
    void aPrivateChestTheWholeRoomAndOtherFurniStayBehindRights() {
        assertFalse(WiredChestRoomLogsEvent.mayReadChestLog(roomWith(7, chest(false)), 7));
        assertFalse(WiredChestRoomLogsEvent.mayReadChestLog(roomWith(7, chest(true)), 0));
        assertFalse(WiredChestRoomLogsEvent.mayReadChestLog(roomWith(7, mock(UserItem.class)), 7));
        assertFalse(WiredChestRoomLogsEvent.mayReadChestLog(roomWith(7, null), 7));
    }

    @Test
    void theRightsCheckComesBeforeTheLogIsRead() throws Exception {
        String handler = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/rooms/items/WiredChestRoomLogsEvent.java"));

        assertTrue(handler.indexOf("if (!room.hasRights(user) && !mayReadChestLog(room, chestId)) return;")
                < handler.indexOf("ChestTransactionLog.transactions("));
    }
}
