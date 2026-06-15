package com.eu.habbo.habbohotel.earnings;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
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

public class EarningsCenterManager {
    public static final String CONFIG_PREFIX = "earnings.";
    private static final int DEFAULT_COOLDOWN_SECONDS = 86400;
    private static final int DEFAULT_POINTS_TYPE = 5;
    private static final int MAX_CONFIGURED_REWARD = 1_000_000;
    private static final int MAX_ITEM_QUANTITY = 100;
    private static final int MAX_HC_DAYS = 365;

    private final ConfigSource config;
    private final ClaimRepository claims;
    private final RewardApplier rewards;
    private final Clock clock;

    public EarningsCenterManager() {
        this(new EmulatorConfigSource(), new JdbcClaimRepository(), new HabboRewardApplier(), Clock.systemUTC());
    }

    public EarningsCenterManager(ConfigSource config, ClaimRepository claims, RewardApplier rewards, Clock clock) {
        this.config = config;
        this.claims = claims;
        this.rewards = rewards;
        this.clock = clock;
    }

    public List<EarningsEntry> getEntries(Habbo habbo) {
        int userId = getUserId(habbo);
        int now = now();
        List<EarningsEntry> entries = new ArrayList<>();

        for (EarningsCategory category : EarningsCategory.values()) {
            entries.add(buildEntry(userId, category, now));
        }

        return entries;
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
        CategoryDefinition definition = loadDefinition(category);

        if (!definition.enabled()) {
            return new EarningsClaimResult(category, EarningsClaimResult.Status.DISABLED, buildEntry(userId, category, now));
        }

        if (definition.rewards().isEmpty()) {
            return new EarningsClaimResult(category, EarningsClaimResult.Status.NO_REWARD, buildEntry(userId, category, now));
        }

        String periodKey = periodKey(now, definition.cooldownSeconds());

        try {
            if (!this.claims.recordClaim(userId, category.getKey(), periodKey, now)) {
                return new EarningsClaimResult(category, EarningsClaimResult.Status.ALREADY_CLAIMED, buildEntry(userId, category, now));
            }

            this.rewards.grant(habbo, definition.rewards());
            return new EarningsClaimResult(category, EarningsClaimResult.Status.SUCCESS, buildEntry(userId, category, now));
        } catch (SQLException e) {
            try {
                this.claims.removeClaim(userId, category.getKey(), periodKey);
            } catch (SQLException ignored) {
            }
            return new EarningsClaimResult(category, EarningsClaimResult.Status.ERROR, buildEntry(userId, category, now));
        }
    }

    private EarningsEntry buildEntry(int userId, EarningsCategory category, int now) {
        CategoryDefinition definition = loadDefinition(category);
        boolean claimable = false;
        int nextClaimAt = 0;

        if (definition.enabled() && !definition.rewards().isEmpty()) {
            String periodKey = periodKey(now, definition.cooldownSeconds());

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

    private CategoryDefinition loadDefinition(EarningsCategory category) {
        String key = CONFIG_PREFIX + category.getKey() + ".";
        boolean enabled = this.config.getBoolean(CONFIG_PREFIX + "enabled", false)
                && this.config.getBoolean(key + "enabled", true);
        int cooldown = Math.max(60, this.config.getInt(key + "cooldown.seconds", DEFAULT_COOLDOWN_SECONDS));
        int pointsType = Math.max(0, this.config.getInt(key + "points.type", DEFAULT_POINTS_TYPE));
        List<EarningsReward> rewards = new ArrayList<>();

        addReward(rewards, EarningsReward.TYPE_CREDITS, this.config.getInt(key + "credits", 0), 0);
        addReward(rewards, EarningsReward.TYPE_PIXELS, this.config.getInt(key + "pixels", 0), 0);
        addReward(rewards, EarningsReward.TYPE_POINTS, this.config.getInt(key + "points", 0), pointsType);
        addBadgeReward(rewards, this.config.getValue(key + "badge", ""));
        addItemReward(rewards, this.config.getInt(key + "item_id", 0), this.config.getInt(key + "item.quantity", 1));
        addHcReward(rewards, this.config.getInt(key + "hc.days", 0));

        return new CategoryDefinition(enabled, cooldown, rewards);
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

        rewards.add(new EarningsReward(EarningsReward.TYPE_ITEM, Math.min(quantity, MAX_ITEM_QUANTITY), 0, String.valueOf(itemId)));
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

    private String periodKey(int now, int cooldownSeconds) {
        return String.valueOf(now / cooldownSeconds);
    }

    private int nextPeriodStart(int now, int cooldownSeconds) {
        return ((now / cooldownSeconds) + 1) * cooldownSeconds;
    }

    private record CategoryDefinition(boolean enabled, int cooldownSeconds, List<EarningsReward> rewards) {
    }

    public interface ConfigSource {
        boolean getBoolean(String key, boolean defaultValue);

        int getInt(String key, int defaultValue);

        String getValue(String key, String defaultValue);
    }

    public interface ClaimRepository {
        boolean hasClaim(int userId, String category, String periodKey) throws SQLException;

        boolean recordClaim(int userId, String category, String periodKey, int claimedAt) throws SQLException;

        void removeClaim(int userId, String category, String periodKey) throws SQLException;
    }

    public interface RewardApplier {
        void grant(Habbo habbo, List<EarningsReward> rewards) throws SQLException;
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
                 PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users_earnings_claims WHERE user_id = ? AND category = ? AND period_key = ? LIMIT 1")) {
                statement.setInt(1, userId);
                statement.setString(2, category);
                statement.setString(3, periodKey);
                return statement.executeQuery().next();
            }
        }

        @Override
        public boolean recordClaim(int userId, String category, String periodKey, int claimedAt) throws SQLException {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("INSERT INTO users_earnings_claims (user_id, category, period_key, claimed_at) VALUES (?, ?, ?, FROM_UNIXTIME(?))")) {
                statement.setInt(1, userId);
                statement.setString(2, category);
                statement.setString(3, periodKey);
                statement.setInt(4, claimedAt);
                return statement.executeUpdate() == 1;
            } catch (SQLIntegrityConstraintViolationException duplicate) {
                return false;
            }
        }

        @Override
        public void removeClaim(int userId, String category, String periodKey) throws SQLException {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM users_earnings_claims WHERE user_id = ? AND category = ? AND period_key = ? LIMIT 1")) {
                statement.setInt(1, userId);
                statement.setString(2, category);
                statement.setString(3, periodKey);
                statement.executeUpdate();
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
                    case EarningsReward.TYPE_ITEM -> grantItem(habbo, Integer.parseInt(reward.getData()), reward.getAmount());
                    case EarningsReward.TYPE_HC_DAYS -> grantHcDays(habbo, reward.getAmount());
                    default -> {
                    }
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

        private void grantItem(Habbo habbo, int itemId, int quantity) throws SQLException {
            if (!itemExists(itemId)) {
                throw new SQLException("Unknown earnings item reward " + itemId);
            }

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("INSERT INTO items (user_id, item_id, extra_data) VALUES (?, ?, '')")) {
                for (int i = 0; i < quantity; i++) {
                    statement.setInt(1, habbo.getHabboInfo().getId());
                    statement.setInt(2, itemId);
                    statement.addBatch();
                }

                statement.executeBatch();
            }
        }

        private boolean itemExists(int itemId) throws SQLException {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT id FROM items_base WHERE id = ? LIMIT 1")) {
                statement.setInt(1, itemId);
                try (ResultSet set = statement.executeQuery()) {
                    return set.next();
                }
            }
        }

        private void grantHcDays(Habbo habbo, int days) throws SQLException {
            int now = Emulator.getIntUnixTimestamp();
            int current = habbo.getHabboStats().getClubExpireTimestamp();
            int newExpire = (current > now ? current : now) + (days * 86400);

            habbo.getHabboStats().setClubExpireTimestamp(newExpire);

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET club_expire_timestamp = ? WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, newExpire);
                statement.setInt(2, habbo.getHabboInfo().getId());
                statement.executeUpdate();
            }
        }
    }
}
