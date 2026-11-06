package polaris.hotel.earnings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.earnings.IncomeRewardNotificationComposer;

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

    private User player() {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(PLAYER);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getClient()).thenReturn(this.client);
        return user;
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
        User user = this.player();

        assertTrue(earnings.pay(user, 40, WiredEarnings.CATEGORY_AGENCY));
        assertTrue(earnings.pay(user, 2, WiredEarnings.CATEGORY_AGENCY));

        verify(user, never()).giveCredits(anyInt());
        assertEquals(42, earnings.pending(user, EarningsCategory.AGENCY));
        assertEquals(0, earnings.pending(user, EarningsCategory.GAMES));
        ArgumentCaptor<IncomeRewardNotificationComposer> sent =
                ArgumentCaptor.forClass(IncomeRewardNotificationComposer.class);
        verify(this.client, org.mockito.Mockito.times(2)).sendResponse(sent.capture());
        assertEquals(13, sent.getValue().getRewardCategory());
    }

    @Test
    void claimingMovesTheCreditsIntoThePurseOnce() throws SQLException {
        Store store = new Store();
        WiredEarnings earnings = new WiredEarnings(windowOn(), store);
        User user = this.player();
        earnings.pay(user, 25, WiredEarnings.CATEGORY_GAMES);

        when(user.applyCredits(anyInt(), anyString(), anyString(), any())).thenReturn(true);

        assertTrue(earnings.claim(user, EarningsCategory.GAMES));
        verify(user).applyCredits(eq(25), eq("earnings.wired.games"), anyString(), any());
        assertFalse(earnings.claim(user, EarningsCategory.GAMES), "nothing left");
        assertFalse(earnings.claim(user, EarningsCategory.MARKETPLACE), "not a wired row");
    }

    @Test
    void aCreditThatDoesNotGoThroughLeavesTheRowToClaimAgain() throws SQLException {
        Store store = new Store();
        WiredEarnings earnings = new WiredEarnings(windowOn(), store);
        User user = this.player();
        earnings.pay(user, 25, WiredEarnings.CATEGORY_GAMES);
        when(user.applyCredits(anyInt(), anyString(), anyString(), any())).thenReturn(false);

        assertFalse(earnings.claim(user, EarningsCategory.GAMES));
        assertEquals(25, earnings.pending(user, EarningsCategory.GAMES), "the credits are still waiting");
    }

    @Test
    void withoutAWindowToClaimThemInTheCreditsGoIntoThePurse() {
        User user = this.player();

        assertFalse(new WiredEarnings(new Config(), new Store()).pay(user, 5, 11), "the earnings window is off");
        assertFalse(
                new WiredEarnings(windowOn().with("earnings.games.native.enabled", "0"), new Store()).pay(user, 6, 11),
                "the hotel kept its periodic games reward");
        assertFalse(
                new WiredEarnings(windowOn().with("earnings.agency.enabled", "0"), new Store()).pay(user, 7, 13),
                "the agency row is off");

        verify(user).giveCredits(5);
        verify(user).giveCredits(6);
        verify(user).giveCredits(7);
        verify(this.client, never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void aFailedWriteNeverLosesTheCredits() {
        Store store = new Store();
        store.failing = true;
        User user = this.player();

        assertFalse(new WiredEarnings(windowOn(), store).pay(user, 9, 11));
        verify(user).giveCredits(9);
    }

    @Test
    void nothingIsPaidForNothing() {
        User user = this.player();

        assertFalse(new WiredEarnings(windowOn(), new Store()).pay(user, 0, 11));
        verify(user, never()).giveCredits(anyInt());
        verify(user, never()).giveCredits(anyInt(), anyString());
    }
}
