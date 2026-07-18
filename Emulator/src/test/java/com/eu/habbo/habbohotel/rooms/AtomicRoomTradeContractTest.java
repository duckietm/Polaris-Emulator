package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicRoomTradeContractTest {
    @Test
    void tradePersistenceUsesOneTransaction() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/rooms/RoomTradeTransaction.java"));

        int begin = source.indexOf("connection.setAutoCommit(false)");
        int persist = source.indexOf("persistItems(connection", begin);
        int credit = source.indexOf("creditUser(connection", persist);
        int commit = source.indexOf("connection.commit()");
        int rollback = source.indexOf("connection.rollback()");

        assertTrue(begin > -1, "trade persistence must disable autocommit");
        assertTrue(source.contains("UPDATE items SET user_id = ? WHERE id = ? AND user_id = ? LIMIT 1"));
        assertTrue(source.contains("DELETE FROM items WHERE id = ? AND user_id = ? LIMIT 1"));
        assertTrue(source.contains("EconomyLedger.apply(connection"));
        assertTrue(source.contains("\"room.trade.credit_furni\""));
        assertTrue(persist > begin, "furni must be persisted inside the transaction");
        assertTrue(credit > persist, "trade credits must be granted after item persistence");
        assertTrue(commit > credit, "the transaction must commit only after every economy mutation");
        assertTrue(rollback > begin, "failed trades must roll back");
    }

    @Test
    void roomTradeDoesNotPersistRedeemsOrCreditsAfterTransaction() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/rooms/RoomTrade.java"));

        assertTrue(source.contains("RoomTradeTransaction.execute("));
        assertFalse(source.contains("new QueryDeleteHabboItem(item).run()"));
        assertFalse(source.contains("giveCredits(creditsForUserOne)"));
        assertFalse(source.contains("giveCredits(creditsForUserTwo)"));
    }
}
