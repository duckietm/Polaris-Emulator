package com.eu.habbo.habbohotel.earnings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.earnings.IncomeRewardNotificationComposer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * AIR {@code EarningsController}: IncomeRewardNotification means "a new reward is waiting" (bubble + purse
 * indicator), so it is sent when a reward appears and the row can be claimed, never for a claim.
 */
class EarningsNotificationsTest {
    private final GameClient client = mock(GameClient.class);
    private final EarningsCenterManager manager = mock(EarningsCenterManager.class);

    private Habbo player() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(3);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getClient()).thenReturn(this.client);
        EarningsNotifications.use(() -> this.manager);
        return habbo;
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
        Habbo habbo = this.player();
        when(this.manager.isClaimable(habbo, EarningsCategory.MARKETPLACE)).thenReturn(true);

        assertTrue(EarningsNotifications.newReward(habbo, EarningsCategory.MARKETPLACE));

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(this.client).sendResponse(sent.capture());
        assertEquals(3, ((IncomeRewardNotificationComposer) sent.getValue()).getRewardCategory());
    }

    @Test
    void aRowTheWindowCannotClaimStaysQuiet() {
        Habbo habbo = this.player();
        when(this.manager.isClaimable(habbo, EarningsCategory.MARKETPLACE)).thenReturn(false);

        assertFalse(EarningsNotifications.newReward(habbo, EarningsCategory.MARKETPLACE));
        verify(this.client, never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void progressAnnouncesOnlyANewClaimPeriod() {
        Habbo habbo = this.player();
        when(this.manager.isClaimable(habbo, EarningsCategory.ACHIEVEMENTS)).thenReturn(true);

        when(this.manager.progressPeriod(habbo, EarningsCategory.ACHIEVEMENTS)).thenReturn("score:3");
        assertFalse(EarningsNotifications.afterProgress(habbo, EarningsCategory.ACHIEVEMENTS, "score:3"));
        assertFalse(
                EarningsNotifications.afterProgress(habbo, EarningsCategory.ACHIEVEMENTS, null), "not a progress row");
        verify(this.client, never()).sendResponse(any(MessageComposer.class));

        assertTrue(EarningsNotifications.afterProgress(habbo, EarningsCategory.ACHIEVEMENTS, "score:2"));
        verify(this.client).sendResponse(any(MessageComposer.class));
    }

    @Test
    void anOfflinePlayerIsNotTold() {
        assertFalse(EarningsNotifications.newReward(null, EarningsCategory.MARKETPLACE));
        Habbo habbo = this.player();
        when(habbo.getClient()).thenReturn(null);
        assertFalse(EarningsNotifications.newReward(habbo, EarningsCategory.MARKETPLACE));
    }
}
