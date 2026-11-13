package polaris.hotel.earnings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.catalog.marketplace.MarketPlace;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.hotel.users.subscriptions.SubscriptionClub;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.users.AddUserBadgeComposer;

public class EarningsCenterManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EarningsCenterManager.class);
    public static final String CONFIG_PREFIX = "earnings.";
    private static final int DEFAULT_COOLDOWN_SECONDS = 86400;
    private static final int DEFAULT_POINTS_TYPE = 5;
    private static final int MAX_CONFIGURED_REWARD = 1_000_000;
    private static final int MAX_ITEM_QUANTITY = 100;
    private static final int MAX_HC_DAYS = 365;

    private final ConfigSource config;
    private final ClaimRepository claims;
    private final RewardApplier rewards;
    private final NativeIntegration nativeIntegration;
    private final Clock clock;

    public EarningsCenterManager() {
        this(
                new EmulatorConfigSource(),
                new JdbcClaimRepository(),
                new UserRewardApplier(),
                new DefaultNativeIntegration(),
                Clock.systemUTC());
    }

    public EarningsCenterManager(ConfigSource config, ClaimRepository claims, RewardApplier rewards, Clock clock) {
        this(config, claims, rewards, new NoopNativeIntegration(), clock);
    }

    public EarningsCenterManager(
            ConfigSource config,
            ClaimRepository claims,
            RewardApplier rewards,
            NativeIntegration nativeIntegration,
            Clock clock) {
        this.config = config;
        this.claims = claims;
        this.rewards = rewards;
        this.nativeIntegration = nativeIntegration;
        this.clock = clock;
    }

    public List<EarningsEntry> getEntries(User user) {
        int userId = getUserId(user);
        int now = now();
        List<EarningsEntry> entries = new ArrayList<>();

        for (EarningsCategory category : EarningsCategory.values()) {
            entries.add(buildEntry(user, userId, category, now));
        }

        return entries;
    }

    /**
     * Whether the window collects what the hotel's own source of {@code category} pays (marketplace sales, HC payday,
     * wired credits): the window and the row are on and the row is not swapped for a configured periodic reward.
     */
    public boolean collectsInWindow(EarningsCategory category) {
        return category != null
                && this.config.getBoolean(CONFIG_PREFIX + "enabled", false)
                && this.config.getBoolean(CONFIG_PREFIX + category.getKey() + ".enabled", true)
                && nativeEnabled(category)
                && this.nativeIntegration.handles(category);
    }

    /** Whether the player can claim the row now (what the window would show). */
    public boolean isClaimable(User user, EarningsCategory category) {
        EarningsEntry entry = buildEntry(user, getUserId(user), category, now());
        return entry.isEnabled() && entry.isClaimable();
    }

    /**
     * The claim period of a row that progress opens (achievement score steps, talent levels), or null for a row that
     * time or another source opens. A new period after a change means a new reward may be waiting.
     */
    public String progressPeriod(User user, EarningsCategory category) {
        if (category != EarningsCategory.ACHIEVEMENTS && category != EarningsCategory.LEVEL_PROGRESS) return null;
        if (!nativeEnabled(category) || user == null || user.getUserStats() == null) return null;
        return periodKey(user, category, now(), DEFAULT_COOLDOWN_SECONDS);
    }

    public EarningsClaimResult claim(User user, String categoryKey) {
        Optional<EarningsCategory> requestedCategory = EarningsCategory.fromKey(categoryKey);
        if (requestedCategory.isEmpty()) {
            return new EarningsClaimResult(null, EarningsClaimResult.Status.UNKNOWN_CATEGORY, null);
        }

        return claim(user, requestedCategory.get());
    }

    public List<EarningsClaimResult> claimAll(User user) {
        List<EarningsClaimResult> results = new ArrayList<>();

        for (EarningsCategory category : EarningsCategory.values()) {
            results.add(claim(user, category));
        }

        return results;
    }

    private EarningsClaimResult claim(User user, EarningsCategory category) {
        int userId = getUserId(user);
        int now = now();
        CategoryDefinition definition = loadDefinition(user, category);

        if (!definition.enabled()) {
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.DISABLED, buildEntry(user, userId, category, now));
        }

        if (this.nativeIntegration.handles(category) && nativeEnabled(category)) {
            return claimNative(user, userId, category, now, definition);
        }

        if (definition.rewards().isEmpty()) {
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.NO_REWARD, buildEntry(user, userId, category, now));
        }

        if (!isEligibleForProgressClaim(user, category)) {
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.NO_REWARD, buildEntry(user, userId, category, now));
        }

        String periodKey = periodKey(user, category, now, definition.cooldownSeconds());

        boolean claimRecorded;
        try {
            claimRecorded = this.claims.recordClaim(userId, category.getKey(), periodKey, now);
        } catch (SQLException e) {
            // The claim marker was never written and no reward was applied, so a
            // retry is safe and cannot duplicate anything.
            LOGGER.error(
                    "Earnings claim marker could not be recorded; no reward granted, retry is safe userId={} category={} period={}",
                    userId,
                    category.getKey(),
                    periodKey,
                    e);
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.ERROR, buildEntry(user, userId, category, now));
        }

        if (!claimRecorded) {
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.ALREADY_CLAIMED, buildEntry(user, userId, category, now));
        }

        try {
            this.rewards.grant(user, definition.rewards());
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.SUCCESS, buildEntry(user, userId, category, now));
        } catch (SQLException e) {
            // A reward applier may have completed earlier grants before a later
            // database-backed reward failed. Keep the unique claim marker so a
            // retry cannot duplicate the already-applied portion. Operators can
            // reconcile the retained claim and missing reward from this log.
            LOGGER.error(
                    "Earnings grant failed after claim marker was recorded; retaining claim userId={} category={} period={}",
                    userId,
                    category.getKey(),
                    periodKey,
                    e);
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.ERROR, buildEntry(user, userId, category, now));
        }
    }

    private EarningsClaimResult claimNative(
            User user, int userId, EarningsCategory category, int now, CategoryDefinition definition) {
        try {
            if (definition.rewards().isEmpty() || !this.nativeIntegration.hasClaim(user, category)) {
                return new EarningsClaimResult(
                        category, EarningsClaimResult.Status.NO_REWARD, buildEntry(user, userId, category, now));
            }

            return this.nativeIntegration.claim(user, category)
                    ? new EarningsClaimResult(
                            category, EarningsClaimResult.Status.SUCCESS, buildEntry(user, userId, category, now))
                    : new EarningsClaimResult(
                            category, EarningsClaimResult.Status.ERROR, buildEntry(user, userId, category, now));
        } catch (SQLException e) {
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.ERROR, buildEntry(user, userId, category, now));
        }
    }

    private EarningsEntry buildEntry(User user, int userId, EarningsCategory category, int now) {
        CategoryDefinition definition = loadDefinition(user, category);
        boolean claimable = false;
        int nextClaimAt = 0;

        if (definition.enabled() && !definition.rewards().isEmpty()) {
            if (this.nativeIntegration.handles(category) && nativeEnabled(category)) {
                try {
                    claimable = this.nativeIntegration.hasClaim(user, category);
                } catch (SQLException e) {
                    claimable = false;
                }

                return new EarningsEntry(category, true, claimable, 0, definition.rewards());
            }

            if (!isEligibleForProgressClaim(user, category)) {
                return new EarningsEntry(category, true, false, 0, definition.rewards());
            }

            String periodKey = periodKey(user, category, now, definition.cooldownSeconds());

            try {
                claimable = !this.claims.hasClaim(userId, category.getKey(), periodKey);
                nextClaimAt = claimable ? 0 : nextPeriodStart(now, definition.cooldownSeconds());
            } catch (SQLException e) {
                claimable = false;
                nextClaimAt = nextPeriodStart(now, definition.cooldownSeconds());
            }
        }

        return new EarningsEntry(category, definition.enabled(), claimable, nextClaimAt, definition.rewards());
    }

    private CategoryDefinition loadDefinition(User user, EarningsCategory category) {
        String key = CONFIG_PREFIX + category.getKey() + ".";
        boolean enabled = this.config.getBoolean(CONFIG_PREFIX + "enabled", false)
                && this.config.getBoolean(key + "enabled", true);
        int cooldown = Math.max(60, this.config.getInt(key + "cooldown.seconds", DEFAULT_COOLDOWN_SECONDS));
        int pointsType = Math.max(0, this.config.getInt(key + "points.type", DEFAULT_POINTS_TYPE));
        List<EarningsReward> rewards = new ArrayList<>();

        if (nativeEnabled(category) && this.nativeIntegration.handles(category)) {
            try {
                rewards.addAll(this.nativeIntegration.rewards(user, category));
            } catch (SQLException e) {
                LOGGER.warn("Earnings rewards for {} could not be loaded", category.getKey(), e);
            }
        } else {
            addReward(rewards, EarningsReward.TYPE_CREDITS, this.config.getInt(key + "credits", 0), 0);
            addReward(rewards, EarningsReward.TYPE_PIXELS, this.config.getInt(key + "pixels", 0), 0);
            addReward(rewards, EarningsReward.TYPE_POINTS, this.config.getInt(key + "points", 0), pointsType);
            addBadgeReward(rewards, this.config.getValue(key + "badge", ""));
            addItemReward(
                    rewards, this.config.getInt(key + "item_id", 0), this.config.getInt(key + "item.quantity", 1));
            addHcReward(rewards, this.config.getInt(key + "hc.days", 0));
        }

        return new CategoryDefinition(enabled, cooldown, rewards);
    }

    private boolean nativeEnabled(EarningsCategory category) {
        return this.config.getBoolean(CONFIG_PREFIX + category.getKey() + ".native.enabled", true);
    }

    private boolean isEligibleForProgressClaim(User user, EarningsCategory category) {
        if (!nativeEnabled(category) || user == null || user.getUserStats() == null) {
            return true;
        }

        if (category == EarningsCategory.ACHIEVEMENTS) {
            int minimumScore = Math.max(1, this.config.getInt(CONFIG_PREFIX + category.getKey() + ".min_score", 1));
            return user.getUserStats().getAchievementScore() >= minimumScore;
        }

        if (category == EarningsCategory.LEVEL_PROGRESS) {
            int minimumLevel = Math.max(0, this.config.getInt(CONFIG_PREFIX + category.getKey() + ".min_level", 1));
            int highestLevel = Math.max(user.getUserStats().citizenshipLevel, user.getUserStats().helpersLevel);
            return highestLevel >= minimumLevel;
        }

        return true;
    }

    private void addReward(List<EarningsReward> rewards, String type, int amount, int pointsType) {
        int clampedAmount = Math.min(Math.max(0, amount), MAX_CONFIGURED_REWARD);
        if (clampedAmount > 0) {
            rewards.add(new EarningsReward(type, clampedAmount, pointsType));
        }
    }

    private void addBadgeReward(List<EarningsReward> rewards, String badgeCode) {
        if (badgeCode == null || !badgeCode.matches("[A-Za-z0-9_\\-]{1,64}")) {
            return;
        }

        rewards.add(new EarningsReward(EarningsReward.TYPE_BADGE, 1, 0, badgeCode));
    }

    private void addItemReward(List<EarningsReward> rewards, int itemId, int quantity) {
        if (itemId <= 0 || quantity <= 0) {
            return;
        }

        rewards.add(new EarningsReward(
                EarningsReward.TYPE_ITEM, Math.min(quantity, MAX_ITEM_QUANTITY), 0, String.valueOf(itemId)));
    }

    private void addHcReward(List<EarningsReward> rewards, int days) {
        if (days <= 0) {
            return;
        }

        rewards.add(new EarningsReward(EarningsReward.TYPE_HC_DAYS, Math.min(days, MAX_HC_DAYS), 0));
    }

    private int getUserId(User user) {
        if (user == null || user.getUserInfo() == null) {
            return 0;
        }

        return user.getUserInfo().getId();
    }

    private int now() {
        return (int) (this.clock.instant().getEpochSecond());
    }

    private String periodKey(User user, EarningsCategory category, int now, int cooldownSeconds) {
        if (nativeEnabled(category) && user != null && user.getUserStats() != null) {
            if (category == EarningsCategory.ACHIEVEMENTS) {
                int scoreStep = Math.max(1, this.config.getInt(CONFIG_PREFIX + category.getKey() + ".score.step", 100));
                return "score:" + (user.getUserStats().getAchievementScore() / scoreStep);
            }

            if (category == EarningsCategory.LEVEL_PROGRESS) {
                int highestLevel = Math.max(user.getUserStats().citizenshipLevel, user.getUserStats().helpersLevel);
                return "level:" + highestLevel;
            }
        }

        return String.valueOf(now / cooldownSeconds);
    }

    private int nextPeriodStart(int now, int cooldownSeconds) {
        return ((now / cooldownSeconds) + 1) * cooldownSeconds;
    }

    private record CategoryDefinition(boolean enabled, int cooldownSeconds, List<EarningsReward> rewards) {}

    public interface ConfigSource {
        boolean getBoolean(String key, boolean defaultValue);

        int getInt(String key, int defaultValue);

        String getValue(String key, String defaultValue);
    }

    public interface ClaimRepository {
        boolean hasClaim(int userId, String category, String periodKey) throws SQLException;

        boolean recordClaim(int userId, String category, String periodKey, int claimedAt) throws SQLException;
    }

    public interface RewardApplier {
        void grant(User user, List<EarningsReward> rewards) throws SQLException;
    }

    public interface NativeIntegration {
        boolean handles(EarningsCategory category);

        boolean hasClaim(User user, EarningsCategory category) throws SQLException;

        List<EarningsReward> rewards(User user, EarningsCategory category) throws SQLException;

        boolean claim(User user, EarningsCategory category) throws SQLException;
    }

    private static class EmulatorConfigSource implements ConfigSource {
        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return Emulator.getConfig().getBoolean(key, defaultValue);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            return Emulator.getConfig().getInt(key, defaultValue);
        }

        @Override
        public String getValue(String key, String defaultValue) {
            return Emulator.getConfig().getValue(key, defaultValue);
        }
    }

    private static class JdbcClaimRepository implements ClaimRepository {
        @Override
        public boolean hasClaim(int userId, String category, String periodKey) throws SQLException {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT 1 FROM users_earnings_claims WHERE user_id = ? AND category = ? AND period_key = ? LIMIT 1")) {
                statement.setInt(1, userId);
                statement.setString(2, category);
                statement.setString(3, periodKey);
                return statement.executeQuery().next();
            }
        }

        @Override
        public boolean recordClaim(int userId, String category, String periodKey, int claimedAt) throws SQLException {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users_earnings_claims (user_id, category, period_key, claimed_at) VALUES (?, ?, ?, FROM_UNIXTIME(?))")) {
                statement.setInt(1, userId);
                statement.setString(2, category);
                statement.setString(3, periodKey);
                statement.setInt(4, claimedAt);
                return statement.executeUpdate() == 1;
            } catch (SQLIntegrityConstraintViolationException duplicate) {
                return false;
            }
        }
    }

    private static class UserRewardApplier implements RewardApplier {
        @Override
        public void grant(User user, List<EarningsReward> rewards) throws SQLException {
            if (user == null) {
                return;
            }

            // Furni first: they are the only reward that can fail halfway, and they are written in one transaction, so
            // a failure leaves the player with none of this claim's rewards rather than with part of them.
            List<EarningsReward> ordered = new ArrayList<>(rewards);
            ordered.sort((a, b) ->
                    Boolean.compare(b.getType() == EarningsReward.TYPE_ITEM, a.getType() == EarningsReward.TYPE_ITEM));

            for (EarningsReward reward : ordered) {
                switch (reward.getType()) {
                    case EarningsReward.TYPE_CREDITS -> user.giveCredits(reward.getAmount());
                    case EarningsReward.TYPE_PIXELS -> user.givePixels(reward.getAmount());
                    case EarningsReward.TYPE_POINTS -> user.givePoints(reward.getPointsType(), reward.getAmount());
                    case EarningsReward.TYPE_BADGE -> grantBadge(user, reward.getData());
                    case EarningsReward.TYPE_ITEM ->
                        grantItem(user, Integer.parseInt(reward.getData()), reward.getAmount());
                    case EarningsReward.TYPE_HC_DAYS -> grantHcDays(user, reward.getAmount());
                    default -> {}
                }
            }
        }

        private void grantBadge(User user, String badgeCode) throws SQLException {
            if (user.getInventory().getBadgesComponent().hasBadge(badgeCode)) {
                return;
            }

            UserBadge badge = BadgesComponent.createBadge(badgeCode, user);
            if (badge == null) {
                // The claim reports the failure instead of a badge that would be gone at the next login.
                throw new SQLException("Could not write earnings badge " + badgeCode);
            }
            if (user.getClient() != null) {
                user.getClient().sendResponse(new AddUserBadgeComposer(badge));
            }
        }

        /**
         * Creates the furni and puts it in the open inventory. Writing only the database row left it invisible until
         * the next login. All the copies are written in one transaction: a copy that failed used to leave the earlier
         * ones in the inventory while the claim reported an error.
         */
        private void grantItem(User user, int itemId, int quantity) throws SQLException {
            // WiredEarnings in this package reaches the hotel through WiredPlatform too.
            GameEnvironment environment = WiredPlatform.gameEnvironment();
            ItemManager itemManager = environment == null ? null : environment.getItemManager();
            Item baseItem = itemManager == null ? null : itemManager.getItem(itemId);
            if (baseItem == null) {
                throw new SQLException("Unknown earnings item reward " + itemId);
            }

            List<UserItem> created = new ArrayList<>();
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    for (int i = 0; i < quantity; i++) {
                        UserItem item = itemManager.createItem(
                                connection, user.getUserInfo().getId(), baseItem, 0, 0, "");
                        if (item == null) {
                            throw new SQLException("Earnings item reward " + itemId + " could not be created");
                        }
                        created.add(item);
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
            }

            user.getInventory().getItemsComponent().addItems(created);
            if (user.getClient() != null) {
                user.getClient().sendResponse(new AddUserItemComposer(created));
                user.getClient().sendResponse(new InventoryRefreshComposer());
            }
        }

        private void grantHcDays(User user, int days) throws SQLException {
            int now = Emulator.getIntUnixTimestamp();
            int current = user.getUserStats().getClubExpireTimestamp();
            int newExpire = (current > now ? current : now) + (days * 86400);

            user.getUserStats().setClubExpireTimestamp(newExpire);

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE users_settings SET club_expire_timestamp = ? WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, newExpire);
                statement.setInt(2, user.getUserInfo().getId());
                statement.executeUpdate();
            }
        }
    }

    private static class NoopNativeIntegration implements NativeIntegration {
        @Override
        public boolean handles(EarningsCategory category) {
            return false;
        }

        @Override
        public boolean hasClaim(User user, EarningsCategory category) {
            return false;
        }

        @Override
        public List<EarningsReward> rewards(User user, EarningsCategory category) {
            return List.of();
        }

        @Override
        public boolean claim(User user, EarningsCategory category) {
            return false;
        }
    }

    private static class DefaultNativeIntegration implements NativeIntegration {
        @Override
        public boolean handles(EarningsCategory category) {
            return category == EarningsCategory.MARKETPLACE
                    || category == EarningsCategory.HC_PAYDAY
                    || WiredEarnings.airCategoryOf(category) >= 0;
        }

        @Override
        public boolean hasClaim(User user, EarningsCategory category) throws SQLException {
            return !rewards(user, category).isEmpty();
        }

        @Override
        public List<EarningsReward> rewards(User user, EarningsCategory category) throws SQLException {
            if (user == null) {
                return List.of();
            }

            // The games and agency rows hold the credits wired handed out, until they are claimed.
            if (WiredEarnings.airCategoryOf(category) >= 0) {
                int credits = WiredEarnings.get().pending(user, category);
                return credits > 0 ? List.of(new EarningsReward(EarningsReward.TYPE_CREDITS, credits, 0)) : List.of();
            }

            if (category == EarningsCategory.MARKETPLACE) {
                int soldPriceTotal = user.getInventory().getSoldPriceTotal();
                if (soldPriceTotal <= 0) {
                    return List.of();
                }

                if (MarketPlace.MARKETPLACE_CURRENCY == 0) {
                    return List.of(new EarningsReward(EarningsReward.TYPE_CREDITS, soldPriceTotal, 0));
                }

                return List.of(new EarningsReward(
                        EarningsReward.TYPE_POINTS, soldPriceTotal, MarketPlace.MARKETPLACE_CURRENCY));
            }

            if (category == EarningsCategory.HC_PAYDAY) {
                return hcPaydayRewards(user);
            }

            return List.of();
        }

        @Override
        public boolean claim(User user, EarningsCategory category) throws SQLException {
            if (user == null || user.getClient() == null) {
                return false;
            }

            if (WiredEarnings.airCategoryOf(category) >= 0) {
                return WiredEarnings.get().claim(user, category);
            }

            if (category == EarningsCategory.MARKETPLACE) {
                if (user.getInventory().getSoldPriceTotal() <= 0) {
                    return false;
                }

                // Only a payout that went through is a successful claim.
                return MarketPlace.claimSoldCredits(user.getClient()) > 0;
            }

            if (category == EarningsCategory.HC_PAYDAY) {
                if (hcPaydayRewards(user).isEmpty()) {
                    return false;
                }

                // Only the payday rows: the club gift popup and the HC achievement belong to the login.
                return SubscriptionClub.claimUnclaimedPayDays(user) > 0;
            }

            return false;
        }

        private List<EarningsReward> hcPaydayRewards(User user) throws SQLException {
            List<EarningsReward> rewards = new ArrayList<>();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT currency, SUM(total_payout) AS amount FROM logs_hc_payday WHERE user_id = ? AND claimed = 0 GROUP BY currency")) {
                statement.setInt(1, user.getUserInfo().getId());

                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        EarningsReward reward = currencyReward(set.getString("currency"), set.getInt("amount"));
                        if (reward != null) {
                            rewards.add(reward);
                        }
                    }
                }
            }

            return rewards;
        }

        private EarningsReward currencyReward(String currency, int amount) {
            if (amount <= 0) {
                return null;
            }

            String normalized = currency == null ? "" : currency.trim().toLowerCase();
            return switch (normalized) {
                case "credits", "credit", "coins", "coin" -> new EarningsReward(EarningsReward.TYPE_CREDITS, amount, 0);
                case "duckets", "ducket", "pixels", "pixel" ->
                    new EarningsReward(EarningsReward.TYPE_PIXELS, amount, 0);
                case "diamonds", "diamond" -> new EarningsReward(EarningsReward.TYPE_POINTS, amount, 5);
                default -> {
                    try {
                        yield new EarningsReward(
                                EarningsReward.TYPE_POINTS, amount, Math.max(0, Integer.parseInt(normalized)));
                    } catch (NumberFormatException e) {
                        yield null;
                    }
                }
            };
        }
    }
}
