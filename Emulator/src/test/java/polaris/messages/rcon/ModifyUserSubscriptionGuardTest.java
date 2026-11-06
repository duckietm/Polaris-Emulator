package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.subscriptions.Subscription;

class ModifyUserSubscriptionGuardTest {
    @Test
    void validatesDurationAgainstConfiguredCeiling() {
        assertTrue(ModifyUserSubscription.isValidDuration(1, 10));
        assertTrue(ModifyUserSubscription.isValidDuration(10, 10));
        assertFalse(ModifyUserSubscription.isValidDuration(0, 10));
        assertFalse(ModifyUserSubscription.isValidDuration(-1, 10));
        assertFalse(ModifyUserSubscription.isValidDuration(11, 10));
    }

    @Test
    void parsesInvalidDurationCeilingsAsDefault() {
        assertEquals(
                ModifyUserSubscription.DEFAULT_MAX_DURATION_SECONDS, ModifyUserSubscription.parseMaxDuration(null));
        assertEquals(ModifyUserSubscription.DEFAULT_MAX_DURATION_SECONDS, ModifyUserSubscription.parseMaxDuration("0"));
        assertEquals(60, ModifyUserSubscription.parseMaxDuration("60"));
    }

    @Test
    void clampsPartialRemovalToRemainingSubscriptionTime() throws Exception {
        var constructor = UserInfo.class.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        UserInfo info = constructor.newInstance(42, 0);
        UserStats stats = mock(UserStats.class, CALLS_REAL_METHODS);
        var owner = UserStats.class.getDeclaredField("userInfo");
        owner.setAccessible(true);
        owner.set(stats, info);
        Subscription subscription = mock(Subscription.class);
        stats.subscriptions = java.util.Set.of(subscription);
        when(subscription.getSubscriptionType()).thenReturn(Subscription.CLUB);
        when(subscription.isActive()).thenReturn(true);
        when(subscription.getRemaining()).thenReturn(5);

        assertEquals(subscription, stats.removeSubscription(Subscription.CLUB, 86400));
        verify(subscription).addDuration(-5);
    }
}
