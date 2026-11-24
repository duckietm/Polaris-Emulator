package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RecyclerRulesTest {
    @Test
    void costAndWaitAreOffUnlessSetAndNeverNegative() {
        assertEquals(0, RecyclerRules.clampCost(0));
        assertEquals(0, RecyclerRules.clampCost(-5));
        assertEquals(10, RecyclerRules.clampCost(10));
        assertEquals(0, RecyclerRules.clampTimeout(-1));
        assertEquals(10, RecyclerRules.clampTimeout(10));
        assertEquals(RecyclerRules.MAX_TIMEOUT_SECONDS, RecyclerRules.clampTimeout(Integer.MAX_VALUE));
    }

    @Test
    void theWaitRunsFromTheExchangeAndEnds() {
        int user = 910_001;

        assertEquals(0, RecyclerRules.secondsToWait(user, 1000));

        RecyclerRules.exchanged(user, 1000, 10);
        assertEquals(10, RecyclerRules.secondsToWait(user, 1000));
        assertEquals(3, RecyclerRules.secondsToWait(user, 1007));
        assertEquals(0, RecyclerRules.secondsToWait(user, 1010));
        assertEquals(0, RecyclerRules.secondsToWait(user, 1005));
    }

    @Test
    void noWaitIsKeptWhenTheSettingIsOff() {
        int user = 910_002;

        RecyclerRules.exchanged(user, 1000, 10);
        RecyclerRules.exchanged(user, 1001, 0);
        assertEquals(0, RecyclerRules.secondsToWait(user, 1002));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theExchangeChecksTheWaitAndTheDucketsBeforeItTakesTheFurni() throws Exception {
        String handler = source("polaris/messages/incoming/catalog/recycler/RecycleEvent.java");
        int take = handler.indexOf("takeUserItemsAtomically(items)");

        assertTrue(handler.indexOf("RecyclerRules.secondsToWait(") > 0);
        assertTrue(handler.indexOf("RecyclerRules.secondsToWait(") < take);
        assertTrue(handler.indexOf("getCurrencyAmount(RecyclerRules.DUCKETS) < cost") < take);
        // Charged and timed only once the box exists.
        int consumed = handler.indexOf("consumed = true;");
        assertTrue(handler.indexOf("givePoints(RecyclerRules.DUCKETS, -cost") > consumed);
        assertTrue(handler.indexOf("RecyclerRules.exchanged(") > consumed);
        // The furni leave in the order the client sent them, and the finished packet names the box.
        assertTrue(handler.contains("new LinkedHashSet<>()"));
        assertTrue(handler.contains(
                "new RecyclerCompleteComposer(RecyclerCompleteComposer.RECYCLING_COMPLETE, reward.getId())"));
    }

    @Test
    void theStatusSaysClosedOrHowLongToWait() throws Exception {
        String status = source("polaris/messages/outgoing/catalog/ReloadRecyclerComposer.java");

        assertTrue(status.contains("STATUS_ENABLED = 1"));
        assertTrue(status.contains("STATUS_DISABLED = 2"));
        assertTrue(status.contains("STATUS_TIMEOUT = 3"));
    }
}
