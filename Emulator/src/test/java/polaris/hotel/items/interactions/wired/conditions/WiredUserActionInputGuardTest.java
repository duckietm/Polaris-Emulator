package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WiredUserActionInputGuardTest {

    @Test
    void rejectsInvalidOrFutureTimestamps() {
        assertFalse(WiredUserActionInputGuard.isRecentTimestamp(0, 1000, 5000));
        assertFalse(WiredUserActionInputGuard.isRecentTimestamp(1500, 1000, 5000));
        assertFalse(WiredUserActionInputGuard.isRecentTimestamp(900, 1000, 0));
    }

    @Test
    void acceptsTimestampsInsideWindowOnly() {
        assertTrue(WiredUserActionInputGuard.isRecentTimestamp(900, 1000, 5000));
        assertFalse(WiredUserActionInputGuard.isRecentTimestamp(100, 1000, 500));
    }
}
