package com.eu.habbo.habbohotel.earnings;

import com.eu.habbo.habbohotel.earnings.EarningsCenterManager.ClaimRepository;
import com.eu.habbo.habbohotel.earnings.EarningsCenterManager.ConfigSource;
import com.eu.habbo.habbohotel.earnings.EarningsCenterManager.RewardApplier;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarningsCenterManagerTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.ofEpochSecond(1_800_000_000L), ZoneOffset.UTC);

    @Test
    void disabledFeatureReturnsDisabledEntriesAndRejectsClaims() {
        TestConfig config = new TestConfig().with("earnings.enabled", "0");
        TestClaims claims = new TestClaims();
        TestRewards rewards = new TestRewards();
        EarningsCenterManager manager = new EarningsCenterManager(config, claims, rewards, FIXED_CLOCK);

        List<EarningsEntry> entries = manager.getEntries(null);
        EarningsClaimResult result = manager.claim(null, "daily_gift");

        assertFalse(entries.getFirst().isEnabled());
        assertFalse(entries.getFirst().isClaimable());
        assertEquals(EarningsClaimResult.Status.DISABLED, result.getStatus());
        assertTrue(rewards.granted.isEmpty());
    }

    @Test
    void unknownCategoryIsRejected() {
        EarningsCenterManager manager = new EarningsCenterManager(enabledConfig(), new TestClaims(), new TestRewards(), FIXED_CLOCK);

        EarningsClaimResult result = manager.claim(null, "not_real");

        assertEquals(EarningsClaimResult.Status.UNKNOWN_CATEGORY, result.getStatus());
    }

    @Test
    void successfulClaimGrantsConfiguredRewardOnce() {
        TestConfig config = enabledConfig()
                .with("earnings.daily_gift.credits", "25")
                .with("earnings.daily_gift.points", "3")
                .with("earnings.daily_gift.points.type", "7");
        TestClaims claims = new TestClaims();
        TestRewards rewards = new TestRewards();
        EarningsCenterManager manager = new EarningsCenterManager(config, claims, rewards, FIXED_CLOCK);

        EarningsClaimResult first = manager.claim(null, "daily_gift");
        EarningsClaimResult duplicate = manager.claim(null, "daily_gift");

        assertEquals(EarningsClaimResult.Status.SUCCESS, first.getStatus());
        assertEquals(EarningsClaimResult.Status.ALREADY_CLAIMED, duplicate.getStatus());
        assertEquals(2, rewards.granted.size());
        assertEquals(EarningsReward.TYPE_CREDITS, rewards.granted.get(0).getType());
        assertEquals(25, rewards.granted.get(0).getAmount());
        assertEquals(EarningsReward.TYPE_POINTS, rewards.granted.get(1).getType());
        assertEquals(7, rewards.granted.get(1).getPointsType());
    }

    @Test
    void categoryWithNoConfiguredRewardIsNotClaimable() {
        EarningsCenterManager manager = new EarningsCenterManager(enabledConfig(), new TestClaims(), new TestRewards(), FIXED_CLOCK);

        EarningsClaimResult result = manager.claim(null, "games");

        assertEquals(EarningsClaimResult.Status.NO_REWARD, result.getStatus());
        assertFalse(result.getEntry().isClaimable());
    }

    @Test
    void configurableBadgeItemAndHcRewardsAreIncludedInEntryState() {
        TestConfig config = enabledConfig()
                .with("earnings.bonus_bag.badge", "ACH_Test1")
                .with("earnings.bonus_bag.item_id", "123")
                .with("earnings.bonus_bag.item.quantity", "2")
                .with("earnings.bonus_bag.hc.days", "7");
        EarningsCenterManager manager = new EarningsCenterManager(config, new TestClaims(), new TestRewards(), FIXED_CLOCK);

        EarningsEntry entry = manager.getEntries(null).stream()
                .filter(current -> current.getCategory() == EarningsCategory.BONUS_BAG)
                .findFirst()
                .orElseThrow();

        assertTrue(entry.isClaimable());
        assertEquals(3, entry.getRewards().size());
        assertEquals(EarningsReward.TYPE_BADGE, entry.getRewards().get(0).getType());
        assertEquals("ACH_Test1", entry.getRewards().get(0).getData());
        assertEquals(EarningsReward.TYPE_ITEM, entry.getRewards().get(1).getType());
        assertEquals("123", entry.getRewards().get(1).getData());
        assertEquals(2, entry.getRewards().get(1).getAmount());
        assertEquals(EarningsReward.TYPE_HC_DAYS, entry.getRewards().get(2).getType());
        assertEquals(7, entry.getRewards().get(2).getAmount());
    }

    @Test
    void failedRewardGrantRollsBackClaimRecord() {
        TestConfig config = enabledConfig().with("earnings.daily_gift.credits", "10");
        TestClaims claims = new TestClaims();
        EarningsCenterManager manager = new EarningsCenterManager(config, claims, (habbo, rewards) -> {
            throw new SQLException("grant failed");
        }, FIXED_CLOCK);

        EarningsClaimResult failed = manager.claim(null, "daily_gift");
        EarningsClaimResult retried = new EarningsCenterManager(config, claims, new TestRewards(), FIXED_CLOCK)
                .claim(null, "daily_gift");

        assertEquals(EarningsClaimResult.Status.ERROR, failed.getStatus());
        assertEquals(EarningsClaimResult.Status.SUCCESS, retried.getStatus());
    }

    @Test
    void claimAllGrantsClaimableRowsAndSkipsAlreadyClaimedRows() throws SQLException {
        TestConfig config = enabledConfig()
                .with("earnings.daily_gift.credits", "10")
                .with("earnings.games.pixels", "4");
        TestClaims claims = new TestClaims();
        TestRewards rewards = new TestRewards();
        EarningsCenterManager manager = new EarningsCenterManager(config, claims, rewards, FIXED_CLOCK);

        claims.recordClaim(0, "daily_gift", String.valueOf(1_800_000_000L / 86400), 1_800_000_000);
        List<EarningsClaimResult> results = manager.claimAll(null);

        assertEquals(EarningsClaimResult.Status.ALREADY_CLAIMED, results.get(0).getStatus());
        assertEquals(EarningsClaimResult.Status.SUCCESS, results.get(1).getStatus());
        assertEquals(1, rewards.granted.size());
        assertEquals(EarningsReward.TYPE_PIXELS, rewards.granted.getFirst().getType());
        assertEquals(4, rewards.granted.getFirst().getAmount());
    }

    private static TestConfig enabledConfig() {
        return new TestConfig().with("earnings.enabled", "1");
    }

    private static class TestConfig implements ConfigSource {
        private final Map<String, String> values = new HashMap<>();

        TestConfig with(String key, String value) {
            this.values.put(key, value);
            return this;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return this.values.getOrDefault(key, defaultValue ? "1" : "0").equals("1");
        }

        @Override
        public int getInt(String key, int defaultValue) {
            return Integer.parseInt(this.values.getOrDefault(key, String.valueOf(defaultValue)));
        }

        @Override
        public String getValue(String key, String defaultValue) {
            return this.values.getOrDefault(key, defaultValue);
        }
    }

    private static class TestClaims implements ClaimRepository {
        private final Set<String> claims = new HashSet<>();

        @Override
        public boolean hasClaim(int userId, String category, String periodKey) {
            return this.claims.contains(key(userId, category, periodKey));
        }

        @Override
        public boolean recordClaim(int userId, String category, String periodKey, int claimedAt) {
            return this.claims.add(key(userId, category, periodKey));
        }

        @Override
        public void removeClaim(int userId, String category, String periodKey) {
            this.claims.remove(key(userId, category, periodKey));
        }

        private String key(int userId, String category, String periodKey) {
            return userId + ":" + category + ":" + periodKey;
        }
    }

    private static class TestRewards implements RewardApplier {
        private final List<EarningsReward> granted = new ArrayList<>();

        @Override
        public void grant(com.eu.habbo.habbohotel.users.Habbo habbo, List<EarningsReward> rewards) {
            this.granted.addAll(rewards);
        }
    }
}
