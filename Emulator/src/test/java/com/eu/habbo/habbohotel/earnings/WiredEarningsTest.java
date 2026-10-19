package com.eu.habbo.habbohotel.earnings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.earnings.IncomeRewardNotificationComposer;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * AIR: the credits wired hands out wait in the earnings window, in the row the box's earnings category names (11
 * games, 13 agency), until the player claims them. Without a window to claim them in they go into the purse.
 */
class WiredEarningsTest {
    private static final int PLAYER = 7;

    private static final class Config implements EarningsCenterManager.ConfigSource {
        private final Map<String, String> values = new HashMap<>();

        Config with(String key, String value) {
            this.values.put(key, value);
            return this;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            String value = this.values.get(key);
            return value == null ? defaultValue : "1".equals(value) || "true".equals(value);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            String value = this.values.get(key);
            return value == null ? defaultValue : Integer.parseInt(value);
        }

        @Override
        public String getValue(String key, String defaultValue) {
            return this.values.getOrDefault(key, defaultValue);
        }
    }

    private static final class Store implements WiredEarnings.Store {
        private final Map<String, Integer> rows = new HashMap<>();
        private boolean failing;

        @Override
        public void add(int userId, String category, int credits) throws SQLException {
            if (this.failing) throw new SQLException("down");
            this.rows.merge(userId + ":" + category, credits, Integer::sum);
        }

        @Override
        public int pending(int userId, String category) {
            return this.rows.getOrDefault(userId + ":" + category, 0);
        }

        @Override
        public boolean take(int userId, String category, int credits) {
            String key = userId + ":" + category;
            if (this.rows.getOrDefault(key, 0) != credits) return false;
            this.rows.put(key, 0);
            return true;
        }
    }

    private final GameClient client = mock(GameClient.class);

    private Habbo player() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(PLAYER);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getClient()).thenReturn(this.client);
        return habbo;
    }

    private static Config windowOn() {
        return new Config().with("earnings.enabled", "1");
    }

    @Test
    void theEarningsCategoryNamesTheRow() {
        assertEquals(EarningsCategory.AGENCY, WiredEarnings.categoryFor(WiredEarnings.CATEGORY_AGENCY));
        assertEquals(EarningsCategory.GAMES, WiredEarnings.categoryFor(WiredEarnings.CATEGORY_GAMES));
        assertEquals(EarningsCategory.GAMES, WiredEarnings.categoryFor(0), "anything else is games");
        assertEquals(13, WiredEarnings.airCategoryOf(EarningsCategory.AGENCY));
        assertEquals(-1, WiredEarnings.airCategoryOf(EarningsCategory.MARKETPLACE));
    }

    @Test
    void wiredCreditsWaitInTheirRowAndTheNotificationNamesIt() throws SQLException {
        Store store = new Store();
        WiredEarnings earnings = new WiredEarnings(windowOn(), store);
        Habbo habbo = this.player();

        assertTrue(earnings.pay(habbo, 40, WiredEarnings.CATEGORY_AGENCY));
        assertTrue(earnings.pay(habbo, 2, WiredEarnings.CATEGORY_AGENCY));

        verify(habbo, never()).giveCredits(anyInt());
        assertEquals(42, earnings.pending(habbo, EarningsCategory.AGENCY));
        assertEquals(0, earnings.pending(habbo, EarningsCategory.GAMES));
        ArgumentCaptor<IncomeRewardNotificationComposer> sent =
                ArgumentCaptor.forClass(IncomeRewardNotificationComposer.class);
        verify(this.client, org.mockito.Mockito.times(2)).sendResponse(sent.capture());
        assertEquals(13, sent.getValue().getRewardCategory());
    }

    @Test
    void claimingMovesTheCreditsIntoThePurseOnce() throws SQLException {
        Store store = new Store();
        WiredEarnings earnings = new WiredEarnings(windowOn(), store);
        Habbo habbo = this.player();
        earnings.pay(habbo, 25, WiredEarnings.CATEGORY_GAMES);

        assertTrue(earnings.claim(habbo, EarningsCategory.GAMES));
        verify(habbo).giveCredits(25, "earnings.wired.games");
        assertFalse(earnings.claim(habbo, EarningsCategory.GAMES), "nothing left");
        assertFalse(earnings.claim(habbo, EarningsCategory.MARKETPLACE), "not a wired row");
    }

    @Test
    void withoutAWindowToClaimThemInTheCreditsGoIntoThePurse() {
        Habbo habbo = this.player();

        assertFalse(new WiredEarnings(new Config(), new Store()).pay(habbo, 5, 11), "the earnings window is off");
        assertFalse(
                new WiredEarnings(windowOn().with("earnings.games.native.enabled", "0"), new Store()).pay(habbo, 6, 11),
                "the hotel kept its periodic games reward");
        assertFalse(
                new WiredEarnings(windowOn().with("earnings.agency.enabled", "0"), new Store()).pay(habbo, 7, 13),
                "the agency row is off");

        verify(habbo).giveCredits(5);
        verify(habbo).giveCredits(6);
        verify(habbo).giveCredits(7);
        verify(this.client, never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void aFailedWriteNeverLosesTheCredits() {
        Store store = new Store();
        store.failing = true;
        Habbo habbo = this.player();

        assertFalse(new WiredEarnings(windowOn(), store).pay(habbo, 9, 11));
        verify(habbo).giveCredits(9);
    }

    @Test
    void nothingIsPaidForNothing() {
        Habbo habbo = this.player();

        assertFalse(new WiredEarnings(windowOn(), new Store()).pay(habbo, 0, 11));
        verify(habbo, never()).giveCredits(anyInt());
        verify(habbo, never()).giveCredits(anyInt(), anyString());
    }
}
