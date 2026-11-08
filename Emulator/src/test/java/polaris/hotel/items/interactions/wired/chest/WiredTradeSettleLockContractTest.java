package polaris.hotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A settlement and a cancel on another thread (timeout, leaving the room) share the session's lock: before, the cancel
 * could hand an item back between its deposit into a chest and its release from the table.
 */
class WiredTradeSettleLockContractTest {
    private static final Path DIR = Path.of("src/main/java/polaris/hotel/items/interactions/wired/chest");

    @Test
    void theSettlementHoldsTheLockTheHandBackTakes() throws Exception {
        String manager = Files.readString(DIR.resolve("WiredTradingManager.java"));
        String settle = manager.substring(manager.indexOf("public boolean settle(User user, long nowMillis)"));
        settle = settle.substring(0, settle.indexOf("private boolean settleHeld"));
        assertTrue(settle.contains("synchronized (negotiation.session())"));
        assertTrue(settle.contains("getNegotiation(user) != negotiation"));

        String session = Files.readString(DIR.resolve("WiredTradingSession.java"));
        assertTrue(session.contains("public synchronized List<Integer> close()"));
    }
}
