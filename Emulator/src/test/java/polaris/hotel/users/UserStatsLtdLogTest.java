package polaris.hotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The cleaner thread rebuilt the LTD log and assigned it while the buyer's purchase added to the old map, so a
 * purchase logged in between was lost and the daily limit could be passed.
 */
class UserStatsLtdLogTest {

    private static UserStats stats() {
        UserStats stats = mock(UserStats.class, CALLS_REAL_METHODS);
        stats.ltdPurchaseLog = new HashMap<>();
        return stats;
    }

    @Test
    void pruningKeepsTheSameLogAndOnlyDropsOldPurchases() {
        UserStats stats = stats();
        Map<Integer, List<Integer>> log = stats.ltdPurchaseLog;
        stats.addLtdLog(5, 1_000);
        stats.addLtdLog(5, 90_000);
        stats.addLtdLog(6, 1_000);

        stats.pruneLtdLog(100_000, 24 * 60 * 60);

        // The map a concurrent purchase writes into is the one that stays.
        assertSame(log, stats.ltdPurchaseLog);
        assertEquals(1, stats.totalLtds());
        assertEquals(1, stats.totalLtds(5));
        assertEquals(0, stats.totalLtds(6));
    }
}
