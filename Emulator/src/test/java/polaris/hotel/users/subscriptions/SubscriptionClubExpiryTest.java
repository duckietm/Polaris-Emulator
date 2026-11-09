package polaris.hotel.users.subscriptions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.users.UserStats;

/** An old club row expiring after a new purchase must not take the perks from a member. */
class SubscriptionClubExpiryTest {

    @Test
    void aMemberWithAnotherActiveClubRowKeepsThePerks() {
        UserStats stats = mock(UserStats.class);
        when(stats.hasActiveClub()).thenReturn(true);

        assertFalse(SubscriptionClub.shouldDowngrade(stats));
    }

    @Test
    void aUserWithoutClubIsDowngraded() {
        UserStats stats = mock(UserStats.class);
        when(stats.hasActiveClub()).thenReturn(false);

        assertTrue(SubscriptionClub.shouldDowngrade(stats));
        assertFalse(SubscriptionClub.shouldDowngrade(null), "nothing to downgrade for an unknown user");
    }
}
