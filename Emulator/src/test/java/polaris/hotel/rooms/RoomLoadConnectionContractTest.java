package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A room load holds one pooled connection (and the room lock) while it adds the items. Asking the
 * pool for another connection per item starved the pool when several rooms loaded at once: logins
 * then waited 10 s and failed. Everything the item loop needs is read up front on that connection.
 */
class RoomLoadConnectionContractTest {
    @Test
    void itemLoadingNeverAsksThePoolForASecondConnection() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomItemManager.java"));
        String compact = source.replaceAll("\\s+", "");
        int start = compact.indexOf("publicvoidloadItems(Connectionconnection){");
        int end = compact.indexOf("publicvoidloadWiredData(", start);
        String loadItems = compact.substring(start, end);

        assertTrue(loadItems.contains("this.readBuildersClubItems(connection)"));
        assertTrue(loadItems.contains("this.readOwnerNames(connection,"));
        assertTrue(loadItems.contains("this.ownership.add(item,buildersClub.contains(item.getId()),false)"));
        assertFalse(loadItems.contains("addUserItem("), "addUserItem looks every item up again");
        assertFalse(loadItems.contains("isTrackedItem("));
        assertFalse(loadItems.contains("getOfflineUserInfo("));
    }

    @Test
    void anItemAddedDuringALoadSkipsTheOwnerLookup() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomItemOwnershipService.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(compact.contains("UserInfoowner=lookUp?UserManager.getOfflineUserInfo(item.getUserId()):null;"));
        assertTrue(compact.contains(
                "if(trackedBuildersClub&&item.getUserId()!=BuildersClubRoomSupport.VIRTUAL_OWNER_ID)"));
    }
}
