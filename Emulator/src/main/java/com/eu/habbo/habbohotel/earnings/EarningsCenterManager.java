package com.eu.habbo.habbohotel.earnings;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
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
                new HabboRewardApplier(),
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

    public List<EarningsEntry> getEntries(Habbo habbo) {
        int userId = getUserId(habbo);
        int now = now();
        List<EarningsEntry> entries = new ArrayList<>();

        for (EarningsCategory category : EarningsCategory.values()) {
            entries.add(buildEntry(habbo, userId, category, now));
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
    public boolean isClaimable(Habbo habbo, EarningsCategory category) {
        EarningsEntry entry = buildEntry(habbo, getUserId(habbo), category, now());
        return entry.isEnabled() && entry.isClaimable();
    }

    /**
     * The claim period of a row that progress opens (achievement score steps, talent levels), or null for a row that
     * time or another source opens. A new period after a change means a new reward may be waiting.
     */
    public String progressPeriod(Habbo habbo, EarningsCategory category) {
        if (category != EarningsCategory.ACHIEVEMENTS && category != EarningsCategory.LEVEL_PROGRESS) return null;
        if (!nativeEnabled(category) || habbo == null || habbo.getHabboStats() == null) return null;
        return periodKey(habbo, category, now(), DEFAULT_COOLDOWN_SECONDS);
    }

    public EarningsClaimResult claim(Habbo habbo, String categoryKey) {
        Optional<EarningsCategory> requestedCategory = EarningsCategory.fromKey(categoryKey);
        if (requestedCategory.isEmpty()) {
            return new EarningsClaimResult(null, EarningsClaimResult.Status.UNKNOWN_CATEGORY, null);
        }

        return claim(habbo, requestedCategory.get());
    }

    public List<EarningsClaimResult> claimAll(Habbo habbo) {
        List<EarningsClaimResult> results = new ArrayList<>();

        for (EarningsCategory category : EarningsCategory.values()) {
            results.add(claim(habbo, category));
        }

        return results;
    }

    private EarningsClaimResult claim(Habbo habbo, EarningsCategory category) {
        int userId = getUserId(habbo);
        int now = now();
        CategoryDefinition definition = loadDefinition(habbo, category);

        if (!definition.enabled()) {
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.DISABLED, buildEntry(habbo, userId, category, now));
        }

        if (this.nativeIntegration.handles(category) && nativeEnabled(category)) {
            return claimNative(habbo, userId, category, now, definition);
        }

        if (definition.rewards().isEmpty()) {
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.NO_REWARD, buildEntry(habbo, userId, category, now));
        }

        if (!isEligibleForProgressClaim(habbo, category)) {
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.NO_REWARD, buildEntry(habbo, userId, category, now));
        }

        String periodKey = periodKey(habbo, category, now, definition.cooldownSeconds());

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
                    category, EarningsClaimResult.Status.ERROR, buildEntry(habbo, userId, category, now));
        }

        if (!claimRecorded) {
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.ALREADY_CLAIMED, buildEntry(habbo, userId, category, now));
        }

        try {
            this.rewards.grant(habbo, definition.rewards());
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.SUCCESS, buildEntry(habbo, userId, category, now));
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
                    category, EarningsClaimResult.Status.ERROR, buildEntry(habbo, userId, category, now));
        }
    }

    private EarningsClaimResult claimNative(
            Habbo habbo, int userId, EarningsCategory category, int now, CategoryDefinition definition) {
        try {
            if (definition.rewards().isEmpty() || !this.nativeIntegration.hasClaim(habbo, category)) {
                return new EarningsClaimResult(
                        category, EarningsClaimResult.Status.NO_REWARD, buildEntry(habbo, userId, category, now));
            }

            return this.nativeIntegration.claim(habbo, category)
                    ? new EarningsClaimResult(
                            category, EarningsClaimResult.Status.SUCCESS, buildEntry(habbo, userId, category, now))
                    : new EarningsClaimResult(
                            category, EarningsClaimResult.Status.ERROR, buildEntry(habbo, userId, category, now));
        } catch (SQLException e) {
            return new EarningsClaimResult(
                    category, EarningsClaimResult.Status.ERROR, buildEntry(habbo, userId, category, now));
        }
    }

    private EarningsEntry buildEntry(Habbo habbo, int userId, EarningsCategory category, int now) {
        CategoryDefinition definition = loadDefinition(habbo, category);
        boolean claimable = false;
        int nextClaimAt = 0;

        if (definition.enabled() && !definition.rewards().isEmpty()) {
            if (this.nativeIntegration.handles(category) && nativeEnabled(category)) {
                try {
                    claimable = this.nativeIntegration.hasClaim(habbo, category);
                } catch (SQLException e) {
                    claimable = false;
                }

                return new EarningsEntry(category, true, claimable, 0, definition.rewards());
            }

            if (!isEligibleForProgressClaim(habbo, category)) {
                return new EarningsEntry(category, true, false, 0, definition.rewards());
            }

            String periodKey = periodKey(habbo, category, now, definition.cooldownSeconds());

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

    private CategoryDefinition loadDefinition(Habbo habbo, EarningsCategory category) {
        String key = CONFIG_PREFIX + category.getKey() + ".";
        boolean enabled = this.config.getBoolean(CONFIG_PREFIX + "enabled", false)
                && this.config.getBoolean(key + "enabled", true);
        int cooldown = Math.max(60, this.config.getInt(key + "cooldown.seconds", DEFAULT_COOLDOWN_SECONDS));
        int pointsType = Math.max(0, this.config.getInt(key + "points.type", DEFAULT_POINTS_TYPE));
        List<EarningsReward> rewards = new ArrayList<>();

        if (nativeEnabled(category) && this.nativeIntegration.handles(category)) {
            try {
                rewards.addAll(this.nativeIntegration.rewards(habbo, category));
            } catch (SQLException ignored) {
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

    private boolean isEligibleForProgressClaim(Habbo habbo, EarningsCategory category) {
        if (!nativeEnabled(category) || habbo == null || habbo.getHabboStats() == null) {
            return true;
        }

        if (category == EarningsCategory.ACHIEVEMENTS) {
            int minimumScore = Math.max(1, this.config.getInt(CONFIG_PREFIX + category.getKey() + ".min_score", 1));
            return habbo.getHabboStats().getAchievementScore() >= minimumScore;
        }

        if (category == EarningsCategory.LEVEL_PROGRESS) {
            int minimumLevel = Math.max(0, this.config.getInt(CONFIG_PREFIX + category.getKey() + ".min_level", 1));
            int highestLevel = Math.max(habbo.getHabboStats().citizenshipLevel, habbo.getHabboStats().helpersLevel);
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

    private int getUserId(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null) {
            return 0;
        }

        return habbo.getHabboInfo().getId();
    }

    private int now() {
        return (int) (this.clock.instant().getEpochSecond());
    }

    private String periodKey(Habbo habbo, EarningsCategory category, int now, int cooldownSeconds) {
        if (nativeEnabled(category) && habbo != null && habbo.getHabboStats() != null) {
            if (category == EarningsCategory.ACHIEVEMENTS) {
                int scoreStep = Math.max(1, this.config.getInt(CONFIG_PREFIX + category.getKey() + ".score.step", 100));
                return "score:" + (habbo.getHabboStats().getAchievementScore() / scoreStep);
            }

            if (category == EarningsCategory.LEVEL_PROGRESS) {
                int highestLevel = Math.max(habbo.getHabboStats().citizenshipLevel, habbo.getHabboStats().helpersLevel);
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
        void grant(Habbo habbo, List<EarningsReward> rewards) throws SQLException;
    }

    public interface NativeIntegration {
        boolean handles(EarningsCategory category);

        boolean hasClaim(Habbo habbo, EarningsCategory category) throws SQLException;

        List<EarningsReward> rewards(Habbo habbo, EarningsCategory category) throws SQLException;

        boolean claim(Habbo habbo, EarningsCategory category) throws SQLException;
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

    private static class HabboRewardApplier implements RewardApplier {
        @Override
        public void grant(Habbo habbo, List<EarningsReward> rewards) throws SQLException {
            if (habbo == null) {
                return;
            }

            for (EarningsReward reward : rewards) {
                switch (reward.getType()) {
                    case EarningsReward.TYPE_CREDITS -> habbo.giveCredits(reward.getAmount());
                    case EarningsReward.TYPE_PIXELS -> habbo.givePixels(reward.getAmount());
                    case EarningsReward.TYPE_POINTS -> habbo.givePoints(reward.getPointsType(), reward.getAmount());
                    case EarningsReward.TYPE_BADGE -> grantBadge(habbo, reward.getData());
                    case EarningsReward.TYPE_ITEM ->
                        grantItem(habbo, Integer.parseInt(reward.getData()), reward.getAmount());
                    case EarningsReward.TYPE_HC_DAYS -> grantHcDays(habbo, reward.getAmount());
                    default -> {}
                }
            }
        }

        private void grantBadge(Habbo habbo, String badgeCode) throws SQLException {
            if (habbo.getInventory().getBadgesComponent().hasBadge(badgeCode)) {
                return;
            }

            HabboBadge badge = new HabboBadge(0, badgeCode, 0, habbo);
            badge.run();
            habbo.getInventory().getBadgesComponent().addBadge(badge);
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));
            }
        }

        /**
         * Creates the furni and puts it in the open inventory. Writing only the database row left it invisible until
         * the next login.
         */
        private void grantItem(Habbo habbo, int itemId, int quantity) throws SQLException {
            // WiredEarnings in this package reaches the hotel through WiredPlatform too.
            GameEnvironment environment = WiredPlatform.gameEnvironment();
            ItemManager itemManager = environment == null ? null : environment.getItemManager();
            Item baseItem = itemManager == null ? null : itemManager.getItem(itemId);
            if (baseItem == null) {
                throw new SQLException("Unknown earnings item reward " + itemId);
            }

            List<HabboItem> created = new ArrayList<>();
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                for (int i = 0; i < quantity; i++) {
                    HabboItem item = itemManager.createItem(
                            connection, habbo.getHabboInfo().getId(), baseItem, 0, 0, "");
                    if (item == null) {
                        throw new SQLException("Earnings item reward " + itemId + " could not be created");
                    }
                    created.add(item);
                }
            } finally {
                if (!created.isEmpty()) {
                    habbo.getInventory().getItemsComponent().addItems(created);
                    if (habbo.getClient() != null) {
                        habbo.getClient().sendResponse(new AddHabboItemComposer(created));
                        habbo.getClient().sendResponse(new InventoryRefreshComposer());
                    }
                }
            }
        }

        private void grantHcDays(Habbo habbo, int days) throws SQLException {
            int now = Emulator.getIntUnixTimestamp();
            int current = habbo.getHabboStats().getClubExpireTimestamp();
            int newExpire = (current > now ? current : now) + (days * 86400);

            habbo.getHabboStats().setClubExpireTimestamp(newExpire);

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE users_settings SET club_expire_timestamp = ? WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, newExpire);
                statement.setInt(2, habbo.getHabboInfo().getId());
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
        public boolean hasClaim(Habbo habbo, EarningsCategory category) {
            return false;
        }

        @Override
        public List<EarningsReward> rewards(Habbo habbo, EarningsCategory category) {
            return List.of();
        }

        @Override
        public boolean claim(Habbo habbo, EarningsCategory category) {
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
        public boolean hasClaim(Habbo habbo, EarningsCategory category) throws SQLException {
            return !rewards(habbo, category).isEmpty();
        }

        @Override
        public List<EarningsReward> rewards(Habbo habbo, EarningsCategory category) throws SQLException {
            if (habbo == null) {
                return List.of();
            }

            // The games and agency rows hold the credits wired handed out, until they are claimed.
            if (WiredEarnings.airCategoryOf(category) >= 0) {
                int credits = WiredEarnings.get().pending(habbo, category);
                return credits > 0 ? List.of(new EarningsReward(EarningsReward.TYPE_CREDITS, credits, 0)) : List.of();
            }

            if (category == EarningsCategory.MARKETPLACE) {
                int soldPriceTotal = habbo.getInventory().getSoldPriceTotal();
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
                return hcPaydayRewards(habbo);
            }

            return List.of();
        }

        @Override
        public boolean claim(Habbo habbo, EarningsCategory category) throws SQLException {
            if (habbo == null || habbo.getClient() == null) {
                return false;
            }

            if (WiredEarnings.airCategoryOf(category) >= 0) {
                return WiredEarnings.get().claim(habbo, category);
            }

            if (category == EarningsCategory.MARKETPLACE) {
                if (habbo.getInventory().getSoldPriceTotal() <= 0) {
                    return false;
                }

                // Only a payout that went through is a successful claim.
                return MarketPlace.claimSoldCredits(habbo.getClient()) > 0;
            }

            if (category == EarningsCategory.HC_PAYDAY) {
                if (hcPaydayRewards(habbo).isEmpty()) {
                    return false;
                }

                // Only the payday rows: the club gift popup and the HC achievement belong to the login.
                return SubscriptionHabboClub.claimUnclaimedPayDays(habbo) > 0;
            }

            return false;
        }

        private List<EarningsReward> hcPaydayRewards(Habbo habbo) throws SQLException {
            List<EarningsReward> rewards = new ArrayList<>();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT currency, SUM(total_payout) AS amount FROM logs_hc_payday WHERE user_id = ? AND claimed = 0 GROUP BY currency")) {
                statement.setInt(1, habbo.getHabboInfo().getId());

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
