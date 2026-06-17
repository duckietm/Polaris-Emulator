package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomTradeSafetyContractTest {
    private static String roomTradeSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomTrade.java"));
    }

    @Test
    void sqlFailureStopsBeforeInventoryTransfer() throws Exception {
        String source = roomTradeSource();
        int catchIndex = source.indexOf("catch (SQLException e)");
        int inventoryTransferIndex = source.indexOf("THashSet<HabboItem> itemsUserOne");

        assertTrue(catchIndex > -1, "RoomTrade must handle SQL failures explicitly");
        assertTrue(inventoryTransferIndex > catchIndex, "Inventory transfer should happen after SQL ownership updates");
        assertTrue(source.substring(catchIndex, inventoryTransferIndex).contains("return false"),
                "SQL failures must abort the trade before in-memory inventory/credit transfer");
    }

    @Test
    void itemOwnersChangeOnlyAfterDatabaseBatchSucceeds() throws Exception {
        String source = roomTradeSource();
        int firstOwnerMutation = source.indexOf("item.setUserId(");
        int batchExecution = source.indexOf("statement.executeBatch();");

        assertTrue(firstOwnerMutation > -1, "RoomTrade should update in-memory item owners after commit");
        assertTrue(batchExecution > -1, "RoomTrade should persist item owner changes with a batch update");
        assertTrue(firstOwnerMutation > batchExecution,
                "In-memory item owners must not change until the database batch has succeeded");
    }
}
