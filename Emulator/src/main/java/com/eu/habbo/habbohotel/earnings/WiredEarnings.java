package com.eu.habbo.habbohotel.earnings;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.earnings.IncomeRewardNotificationComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Credits that wired hands out (a reward contract, a trade contract's reward, Give Currency From Chest) go to the
 * player's earnings window, not straight into the purse, the way AIR does it: the reward popup says "you can claim
 * the credits in the earnings window" ({@code wiredrewards.earnings}, a link to {@code habboUI/open/vault}) whenever
 * the reward has a coin node, and the boxes' earnings category ({@code wiredfurni.params.earnings_category.11} /
 * {@code .13}) is the earnings window row they land in: 11 is {@code games}, 13 is {@code agency} (AIR
 * {@code EarningsView._rewardCategories}).
 *
 * <p>The credits wait in {@code users_wired_earnings} until the player claims the row. When the earnings window is
 * off ({@code earnings.enabled}), the row is off ({@code earnings.<row>.enabled}) or the hotel kept the row on its
 * configured periodic reward ({@code earnings.<row>.native.enabled = 0}), there is nowhere to claim them, so they go
 * into the purse at once, as before. A failed write also pays into the purse: credits are never lost.
 */
public class WiredEarnings {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEarnings.class);

    /** AIR earnings categories the wired boxes offer. */
    public static final int CATEGORY_GAMES = 11;

    public static final int CATEGORY_AGENCY = 13;

    private static final AtomicReference<WiredEarnings> CURRENT =
            new AtomicReference<>(new WiredEarnings(new HotelConfig(), new JdbcStore()));

    private final EarningsCenterManager.ConfigSource config;
    private final Store store;

    public WiredEarnings(EarningsCenterManager.ConfigSource config, Store store) {
        this.config = config;
        this.store = store;
    }

    public static WiredEarnings get() {
        return CURRENT.get();
    }

    /** Swap the instance (tests); returns the one it replaced. Null puts the hotel's back. */
    public static WiredEarnings use(WiredEarnings next) {
        return CURRENT.getAndSet(next == null ? new WiredEarnings(new HotelConfig(), new JdbcStore()) : next);
    }

    /** The earnings row of an AIR category: 13 is the agency row, anything else the games row. */
    public static EarningsCategory categoryFor(int airCategory) {
        return airCategory == CATEGORY_AGENCY ? EarningsCategory.AGENCY : EarningsCategory.GAMES;
    }

    /** The AIR category of an earnings row, or -1 for a row wired does not fill. */
    public static int airCategoryOf(EarningsCategory category) {
        if (category == EarningsCategory.GAMES) return CATEGORY_GAMES;
        if (category == EarningsCategory.AGENCY) return CATEGORY_AGENCY;
        return -1;
    }

    /** Whether wired credits of {@code airCategory} wait in the earnings window. */
    public boolean collectsInWindow(int airCategory) {
        String key =
                EarningsCenterManager.CONFIG_PREFIX + categoryFor(airCategory).getKey() + ".";
        return this.config.getBoolean(EarningsCenterManager.CONFIG_PREFIX + "enabled", false)
                && this.config.getBoolean(key + "enabled", true)
                && this.config.getBoolean(key + "native.enabled", true);
    }

    /**
     * Pay wired credits: into the earnings row of {@code airCategory} when the window collects them (the player is
     * told with the official income reward notification), else into the purse.
     *
     * @return true when they went to the earnings window
     */
    public boolean pay(Habbo habbo, int credits, int airCategory) {
        if (habbo == null || habbo.getHabboInfo() == null || credits <= 0) return false;

        if (this.collectsInWindow(airCategory)) {
            int userId = habbo.getHabboInfo().getId();
            String key = categoryFor(airCategory).getKey();
            try {
                this.store.add(userId, key, credits);
                if (habbo.getClient() != null) {
                    habbo.getClient().sendResponse(new IncomeRewardNotificationComposer(normalize(airCategory)));
                }
                return true;
            } catch (SQLException e) {
                LOGGER.error(
                        "Wired earnings could not be stored, paying into the purse instead userId={} category={} credits={}",
                        userId,
                        key,
                        credits,
                        e);
            }
        }

        habbo.giveCredits(credits);
        return false;
    }

    /** The credits waiting in a row. */
    public int pending(Habbo habbo, EarningsCategory category) throws SQLException {
        if (habbo == null || habbo.getHabboInfo() == null || airCategoryOf(category) < 0) return 0;
        return Math.max(0, this.store.pending(habbo.getHabboInfo().getId(), category.getKey()));
    }

    /**
     * Claim a row: the credits move into the purse. Taking them is a compare-and-set on the amount read, so two
     * claims at once cannot both pay.
     *
     * @return false when there was nothing to claim
     */
    public boolean claim(Habbo habbo, EarningsCategory category) throws SQLException {
        int credits = this.pending(habbo, category);
        if (credits <= 0) return false;
        if (!this.store.take(habbo.getHabboInfo().getId(), category.getKey(), credits)) return false;

        habbo.giveCredits(credits, "earnings.wired." + category.getKey());
        return true;
    }

    private static int normalize(int airCategory) {
        return airCategory == CATEGORY_AGENCY ? CATEGORY_AGENCY : CATEGORY_GAMES;
    }

    /** Where the waiting credits live. */
    public interface Store {
        void add(int userId, String category, int credits) throws SQLException;

        int pending(int userId, String category) throws SQLException;

        /** Take exactly {@code credits}; false when the row no longer holds that amount. */
        boolean take(int userId, String category, int credits) throws SQLException;
    }

    private static final class HotelConfig implements EarningsCenterManager.ConfigSource {
        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            ConfigurationManager config = WiredPlatform.configuration();
            return config == null ? defaultValue : config.getBoolean(key, defaultValue);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            ConfigurationManager config = WiredPlatform.configuration();
            return config == null ? defaultValue : config.getInt(key, defaultValue);
        }

        @Override
        public String getValue(String key, String defaultValue) {
            ConfigurationManager config = WiredPlatform.configuration();
            return config == null ? defaultValue : config.getValue(key, defaultValue);
        }
    }

    private static final class JdbcStore implements Store {
        @Override
        public void add(int userId, String category, int credits) throws SQLException {
            Database database = WiredPlatform.database();
            if (database == null) throw new SQLException("no database");
            try (Connection connection = database.getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users_wired_earnings (user_id, category, credits) VALUES (?, ?, ?) "
                                    + "ON DUPLICATE KEY UPDATE credits = LEAST(credits + VALUES(credits), 2147483647)")) {
                statement.setInt(1, userId);
                statement.setString(2, category);
                statement.setInt(3, credits);
                statement.executeUpdate();
            }
        }

        @Override
        public int pending(int userId, String category) throws SQLException {
            Database database = WiredPlatform.database();
            if (database == null) return 0;
            try (Connection connection = database.getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT credits FROM users_wired_earnings WHERE user_id = ? AND category = ? LIMIT 1")) {
                statement.setInt(1, userId);
                statement.setString(2, category);
                try (ResultSet set = statement.executeQuery()) {
                    return set.next() ? set.getInt("credits") : 0;
                }
            }
        }

        @Override
        public boolean take(int userId, String category, int credits) throws SQLException {
            Database database = WiredPlatform.database();
            if (database == null) return false;
            try (Connection connection = database.getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE users_wired_earnings SET credits = 0 WHERE user_id = ? AND category = ? AND credits = ?")) {
                statement.setInt(1, userId);
                statement.setString(2, category);
                statement.setInt(3, credits);
                return statement.executeUpdate() == 1;
            }
        }
    }
}
