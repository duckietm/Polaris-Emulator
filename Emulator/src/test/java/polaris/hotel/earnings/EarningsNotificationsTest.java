package polaris.hotel.earnings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.earnings.IncomeRewardNotificationComposer;

/**
 * AIR {@code EarningsController}: IncomeRewardNotification means "a new reward is waiting" (bubble + purse
 * indicator), so it is sent when a reward appears and the row can be claimed, never for a claim.
 */
class EarningsNotificationsTest {
    private final GameClient client = mock(GameClient.class);
    private final EarningsCenterManager manager = mock(EarningsCenterManager.class);

    private User player() {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(3);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getClient()).thenReturn(this.client);
        EarningsNotifications.use(() -> this.manager);
        return user;
    }

    @AfterEach
    void restore() {
        EarningsNotifications.use(null);
    }

    @Test
    void theByteIsTheAirRewardCategory() {
        assertEquals(2, EarningsNotifications.airCategory(EarningsCategory.ACHIEVEMENTS));
        assertEquals(3, EarningsNotifications.airCategory(EarningsCategory.MARKETPLACE));
        assertEquals(4, EarningsNotifications.airCategory(EarningsCategory.HC_PAYDAY));
        assertEquals(5, EarningsNotifications.airCategory(EarningsCategory.LEVEL_PROGRESS));
        assertEquals(11, EarningsNotifications.airCategory(EarningsCategory.GAMES));
        assertEquals(13, EarningsNotifications.airCategory(EarningsCategory.AGENCY));
    }

    @Test
    void aClaimableNewRewardIsAnnounced() {
        User user = this.player();
        when(this.manager.isClaimable(user, EarningsCategory.MARKETPLACE)).thenReturn(true);

        assertTrue(EarningsNotifications.newReward(user, EarningsCategory.MARKETPLACE));

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(this.client).sendResponse(sent.capture());
        assertEquals(3, ((IncomeRewardNotificationComposer) sent.getValue()).getRewardCategory());
    }

    @Test
    void aRowTheWindowCannotClaimStaysQuiet() {
        User user = this.player();
        when(this.manager.isClaimable(user, EarningsCategory.MARKETPLACE)).thenReturn(false);

        assertFalse(EarningsNotifications.newReward(user, EarningsCategory.MARKETPLACE));
        verify(this.client, never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void progressAnnouncesOnlyANewClaimPeriod() {
        User user = this.player();
        when(this.manager.isClaimable(user, EarningsCategory.ACHIEVEMENTS)).thenReturn(true);

        when(this.manager.progressPeriod(user, EarningsCategory.ACHIEVEMENTS)).thenReturn("score:3");
        assertFalse(EarningsNotifications.afterProgress(user, EarningsCategory.ACHIEVEMENTS, "score:3"));
        assertFalse(
                EarningsNotifications.afterProgress(user, EarningsCategory.ACHIEVEMENTS, null), "not a progress row");
        verify(this.client, never()).sendResponse(any(MessageComposer.class));

        assertTrue(EarningsNotifications.afterProgress(user, EarningsCategory.ACHIEVEMENTS, "score:2"));
        verify(this.client).sendResponse(any(MessageComposer.class));
    }

    @Test
    void anOfflinePlayerIsNotTold() {
        assertFalse(EarningsNotifications.newReward(null, EarningsCategory.MARKETPLACE));
        User user = this.player();
        when(user.getClient()).thenReturn(null);
        assertFalse(EarningsNotifications.newReward(user, EarningsCategory.MARKETPLACE));
    }
}
