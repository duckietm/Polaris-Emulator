package polaris.messages.incoming.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;

/** The duckets are taken before the respects come back, and given back when no replenish is left. */
class ReplenishRespectEventTest {

    private static User user(boolean debitAccepted, boolean replenishLeft) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        UserStats stats = mock(UserStats.class);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getUserStats()).thenReturn(stats);
        when(info.getId()).thenReturn(7);
        when(info.getPixels()).thenReturn(100);
        when(user.tryTakePoints(eq(0), eq(50), anyString(), anyString(), eq(7))).thenReturn(debitAccepted);
        when(stats.replenishRespect(3)).thenReturn(replenishLeft);
        return user;
    }

    @Test
    void aRefusedDebitReplenishesNothing() {
        User user = user(false, true);

        assertFalse(ReplenishRespectEvent.replenish(user, 50, 3));

        verify(user.getUserStats(), never()).replenishRespect(anyInt());
    }

    @Test
    void aPaidReplenishRestoresTheRespects() {
        User user = user(true, true);

        assertTrue(ReplenishRespectEvent.replenish(user, 50, 3));

        verify(user.getUserStats()).replenishRespect(3);
        verify(user, never()).givePixels(anyInt(), anyString());
    }

    @Test
    void aReplenishNoLongerLeftRefundsTheDuckets() {
        User user = user(true, false);

        assertFalse(ReplenishRespectEvent.replenish(user, 50, 3));

        verify(user).givePixels(50, ReplenishRespectEvent.REASON);
    }
}
