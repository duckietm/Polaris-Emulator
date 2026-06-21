package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.sql.Statement;
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
        int batchExecution = source.indexOf("int[] updateCounts = statement.executeBatch();");
        int batchGuard = source.indexOf("allOwnershipUpdatesSucceeded(updateCounts, expectedUpdates)", batchExecution);

        assertTrue(firstOwnerMutation > -1, "RoomTrade should update in-memory item owners after commit");
        assertTrue(batchExecution > -1, "RoomTrade should persist item owner changes with a batch update");
        assertTrue(batchGuard > batchExecution, "RoomTrade must validate every ownership update before mutating memory");
        assertTrue(firstOwnerMutation > batchGuard,
                "In-memory item owners must not change until the database batch has succeeded");
    }

    @Test
    void ownershipUpdatesRequireExpectedDatabaseOwner() throws Exception {
        String source = roomTradeSource();

        assertTrue(source.contains("UPDATE items SET user_id = ? WHERE id = ? AND user_id = ? LIMIT 1"),
                "RoomTrade ownership transfer should only update items still owned by the offering user");
        assertTrue(source.contains("statement.setInt(3, userOneId)"),
                "User one offered items must require user one as the current database owner");
        assertTrue(source.contains("statement.setInt(3, userTwoId)"),
                "User two offered items must require user two as the current database owner");
    }

    @Test
    void zeroBatchUpdatesAbortTheTrade() {
        assertTrue(RoomTrade.allOwnershipUpdatesSucceeded(new int[]{1, Statement.SUCCESS_NO_INFO}, 2));
        assertTrue(!RoomTrade.allOwnershipUpdatesSucceeded(new int[]{1, 0}, 2));
        assertTrue(!RoomTrade.allOwnershipUpdatesSucceeded(new int[]{1}, 2));
        assertTrue(!RoomTrade.allOwnershipUpdatesSucceeded(new int[]{Statement.EXECUTE_FAILED}, 1));
    }
}
