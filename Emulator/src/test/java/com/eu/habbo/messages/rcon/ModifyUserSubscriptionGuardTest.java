package com.eu.habbo.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import org.junit.jupiter.api.Test;

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
        var constructor = HabboInfo.class.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        HabboInfo info = constructor.newInstance(42, 0);
        HabboStats stats = mock(HabboStats.class, CALLS_REAL_METHODS);
        var owner = HabboStats.class.getDeclaredField("habboInfo");
        owner.setAccessible(true);
        owner.set(stats, info);
        Subscription subscription = mock(Subscription.class);
        stats.subscriptions = java.util.Set.of(subscription);
        when(subscription.getSubscriptionType()).thenReturn(Subscription.HABBO_CLUB);
        when(subscription.isActive()).thenReturn(true);
        when(subscription.getRemaining()).thenReturn(5);

        assertEquals(subscription, stats.removeSubscription(Subscription.HABBO_CLUB, 86400));
        verify(subscription).addDuration(-5);
    }
}
