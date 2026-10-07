package com.eu.habbo.habbohotel.rooms;

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
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomItemManager.java"));
        String compact = source.replaceAll("\\s+", "");
        int start = compact.indexOf("publicvoidloadItems(Connectionconnection){");
        int end = compact.indexOf("publicvoidloadWiredData(", start);
        String loadItems = compact.substring(start, end);

        assertTrue(loadItems.contains("this.readBuildersClubItems(connection)"));
        assertTrue(loadItems.contains("this.readOwnerNames(connection,"));
        assertTrue(loadItems.contains("this.ownership.add(item,buildersClub.contains(item.getId()),false)"));
        assertFalse(loadItems.contains("addHabboItem("), "addHabboItem looks every item up again");
        assertFalse(loadItems.contains("isTrackedItem("));
        assertFalse(loadItems.contains("getOfflineHabboInfo("));
    }

    @Test
    void anItemAddedDuringALoadSkipsTheOwnerLookup() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomItemOwnershipService.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(compact.contains("HabboInfoowner=lookUp?HabboManager.getOfflineHabboInfo(item.getUserId()):null;"));
        assertTrue(compact.contains(
                "if(trackedBuildersClub&&item.getUserId()!=BuildersClubRoomSupport.VIRTUAL_OWNER_ID)"));
    }
}
