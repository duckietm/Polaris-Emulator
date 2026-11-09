package polaris.hotel.gameclients;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

/** Every thread sees the same manager, so a ghost session parked by one is found by another. */
class SessionResumeManagerSingletonTest {

    @Test
    void concurrentCallersShareOneManager() throws Exception {
        Set<SessionResumeManager> seen = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            threads.add(Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                seen.add(SessionResumeManager.getInstance());
            }));
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join(10_000);
        }

        assertEquals(1, seen.size());
    }
}
