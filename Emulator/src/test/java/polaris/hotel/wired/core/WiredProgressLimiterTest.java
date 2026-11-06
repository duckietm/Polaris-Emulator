package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** The per-user, per-name allowance the achievement and reward-track boxes share across rooms. */
class WiredProgressLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000L);

    @Test
    void aUserGetsAtMostTheCapInsideOneWindow() {
        WiredProgressLimiter limiter = new WiredProgressLimiter(this.now::get, 100);

        assertEquals(30, limiter.acquire(7, "achievement:A", 30, 50, 60_000));
        assertEquals(20, limiter.acquire(7, "achievement:A", 30, 50, 60_000), "only what is left of the cap");
        assertEquals(0, limiter.acquire(7, "achievement:A", 1, 50, 60_000), "the cap is spent");
    }

    @Test
    void theAllowanceComesBackWhenTheWindowEnds() {
        WiredProgressLimiter limiter = new WiredProgressLimiter(this.now::get, 100);
        limiter.acquire(7, "achievement:A", 50, 50, 60_000);

        this.now.addAndGet(59_999);
        assertEquals(0, limiter.acquire(7, "achievement:A", 1, 50, 60_000));

        this.now.addAndGet(1);
        assertEquals(50, limiter.acquire(7, "achievement:A", 80, 50, 60_000));
    }

    @Test
    void usersAndNamesHaveTheirOwnAllowance() {
        WiredProgressLimiter limiter = new WiredProgressLimiter(this.now::get, 100);
        limiter.acquire(7, "achievement:A", 50, 50, 60_000);

        assertEquals(50, limiter.acquire(8, "achievement:A", 50, 50, 60_000));
        assertEquals(50, limiter.acquire(7, "achievement:B", 50, 50, 60_000));
        assertEquals(50, limiter.acquire(7, "reward_track:A", 50, 50, 60_000));
    }

    @Test
    void nonsenseAsksGetNothing() {
        WiredProgressLimiter limiter = new WiredProgressLimiter(this.now::get, 100);

        assertEquals(0, limiter.acquire(0, "k", 1, 50, 60_000));
        assertEquals(0, limiter.acquire(7, null, 1, 50, 60_000));
        assertEquals(0, limiter.acquire(7, "k", 0, 50, 60_000));
        assertEquals(0, limiter.acquire(7, "k", -5, 50, 60_000));
        assertEquals(0, limiter.acquire(7, "k", 1, 0, 60_000));
        assertEquals(0, limiter.acquire(7, "k", 1, 50, 0));
        assertEquals(0, limiter.trackedWindows());
    }

    @Test
    void aFullTableFailsClosedUntilWindowsExpire() {
        WiredProgressLimiter limiter = new WiredProgressLimiter(this.now::get, 2);
        limiter.acquire(1, "k", 1, 50, 60_000);
        limiter.acquire(2, "k", 1, 50, 60_000);

        assertEquals(0, limiter.acquire(3, "k", 1, 50, 60_000), "no room for a new user while both are live");
        assertEquals(1, limiter.acquire(1, "k", 1, 50, 60_000), "a tracked user still draws on their window");

        this.now.addAndGet(60_000);
        assertEquals(1, limiter.acquire(3, "k", 1, 50, 60_000), "expired windows are pruned");
        assertEquals(1, limiter.trackedWindows());
    }
}
