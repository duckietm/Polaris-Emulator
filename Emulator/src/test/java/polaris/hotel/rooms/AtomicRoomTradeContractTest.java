package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AtomicRoomTradeContractTest {
    @Test
    void tradePersistenceUsesOneTransaction() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomTradeTransaction.java"));
        String compactSource = source.replaceAll("\\s+", "");

        int begin = compactSource.indexOf("connection.setAutoCommit(false)");
        int persist = compactSource.indexOf("persistItems(connection", begin);
        int credit = compactSource.indexOf("creditUser(connection", persist);
        int commit = compactSource.indexOf("connection.commit()");
        int rollback = compactSource.indexOf("connection.rollback()");

        assertTrue(begin > -1, "trade persistence must disable autocommit");
        assertTrue(source.contains("UPDATE items SET user_id = ? WHERE id = ? AND user_id = ? LIMIT 1"));
        assertTrue(source.contains("DELETE FROM items WHERE id = ? AND user_id = ? LIMIT 1"));
        assertTrue(compactSource.contains("EconomyLedger.apply(connection"));
        assertTrue(source.contains("\"room.trade.credit_furni\""));
        assertTrue(persist > begin, "furni must be persisted inside the transaction");
        assertTrue(credit > persist, "trade credits must be granted after item persistence");
        assertTrue(commit > credit, "the transaction must commit only after every economy mutation");
        assertTrue(rollback > begin, "failed trades must roll back");
    }

    @Test
    void roomTradeDoesNotPersistRedeemsOrCreditsAfterTransaction() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomTrade.java"));

        assertTrue(source.contains("RoomTradeTransaction.execute("));
        assertFalse(source.contains("new QueryDeleteUserItem(item).run()"));
        assertFalse(source.contains("giveCredits(creditsForUserOne)"));
        assertFalse(source.contains("giveCredits(creditsForUserTwo)"));
    }
}
