package polaris.hotel.users.subscriptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;

class SubscriptionLongDurationTest {

    @Test
    void aSubscriptionLongerThanTheIntClockDoesNotExpireAtOnce() {
        int now = 1_760_000_000;
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class, CALLS_REAL_METHODS)) {
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(now);

            Subscription subscription = new Subscription(1, 1, "HABBO_CLUB", now, 500_000_000, true);

            assertTrue(subscription.getRemaining() > 0);
            assertEquals(Integer.MAX_VALUE, subscription.getTimestampEnd());
        }
    }

    @Test
    void theLongestDurationEndsAtTheLastIntTimestamp() {
        assertEquals(Integer.MAX_VALUE - 1_760_000_000, Subscription.maxDuration(1_760_000_000));
    }
}
