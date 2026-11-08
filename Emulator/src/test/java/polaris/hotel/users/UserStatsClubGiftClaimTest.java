package polaris.hotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Two club gift packets at once cannot both spend the last claim. */
class UserStatsClubGiftClaimTest {

    private static final int MONTH = 2678400;

    private static UserStats statsWithGifts(int gifts) {
        UserStats stats = mock(UserStats.class, CALLS_REAL_METHODS);
        doReturn(gifts * MONTH).when(stats).getPastTimeAsClub();
        stats.hcGiftsClaimed = 0;
        return stats;
    }

    @Test
    void claimsStopAtTheGiftsEarned() {
        UserStats stats = statsWithGifts(1);

        assertTrue(stats.tryClaimClubGift());
        assertFalse(stats.tryClaimClubGift());

        stats.refundClubGift();
        assertEquals(1, stats.getRemainingClubGifts());
        assertTrue(stats.tryClaimClubGift());
    }

    @Test
    void concurrentClaimsSpendEachGiftOnce() throws Exception {
        UserStats stats = statsWithGifts(2);
        AtomicInteger granted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            threads.add(Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (stats.tryClaimClubGift()) granted.incrementAndGet();
            }));
        }
        start.countDown();
        for (Thread thread : threads) thread.join(10_000);

        assertEquals(2, granted.get());
        assertEquals(2, stats.hcGiftsClaimed);
    }
}
