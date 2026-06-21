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

        assertFalse(entries.getFirst().enabled());
        assertFalse(entries.getFirst().claimable());
        assertEquals(EarningsClaimResult.Status.DISABLED, result.status());
        assertTrue(rewards.granted.isEmpty());
    }

    @Test
    void unknownCategoryIsRejected() {
        EarningsCenterManager manager = new EarningsCenterManager(enabledConfig(), new TestClaims(), new TestRewards(), FIXED_CLOCK);

        EarningsClaimResult result = manager.claim(null, "not_real");

        assertEquals(EarningsClaimResult.Status.UNKNOWN_CATEGORY, result.status());
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

        assertEquals(EarningsClaimResult.Status.SUCCESS, first.status());
        assertEquals(EarningsClaimResult.Status.ALREADY_CLAIMED, duplicate.status());
        assertEquals(2, rewards.granted.size());
        assertEquals(EarningsReward.TYPE_CREDITS, rewards.granted.get(0).type());
        assertEquals(25, rewards.granted.get(0).amount());
        assertEquals(EarningsReward.TYPE_POINTS, rewards.granted.get(1).type());
        assertEquals(7, rewards.granted.get(1).pointsType());
    }

    @Test
    void categoryWithNoConfiguredRewardIsNotClaimable() {
        EarningsCenterManager manager = new EarningsCenterManager(enabledConfig(), new TestClaims(), new TestRewards(), FIXED_CLOCK);

        EarningsClaimResult result = manager.claim(null, "games");

        assertEquals(EarningsClaimResult.Status.NO_REWARD, result.status());
        assertFalse(result.entry().claimable());
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
                .filter(current -> current.category() == EarningsCategory.BONUS_BAG)
                .findFirst()
                .orElseThrow();

        assertTrue(entry.claimable());
        assertEquals(3, entry.rewards().size());
        assertEquals(EarningsReward.TYPE_BADGE, entry.rewards().get(0).type());
        assertEquals("ACH_Test1", entry.rewards().get(0).data());
        assertEquals(EarningsReward.TYPE_ITEM, entry.rewards().get(1).type());
        assertEquals("123", entry.rewards().get(1).data());
        assertEquals(2, entry.rewards().get(1).amount());
        assertEquals(EarningsReward.TYPE_HC_DAYS, entry.rewards().get(2).type());
        assertEquals(7, entry.rewards().get(2).amount());
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

        assertEquals(EarningsClaimResult.Status.ERROR, failed.status());
        assertEquals(EarningsClaimResult.Status.SUCCESS, retried.status());
    }

    @Test
    void nativeMarketplaceRowsUseNativeClaimInsteadOfPeriodicClaimLedger() {
        TestConfig config = enabledConfig().with("earnings.marketplace.native.enabled", "1");
        TestClaims claims = new TestClaims();
        TestNativeIntegration nativeIntegration = new TestNativeIntegration(EarningsCategory.MARKETPLACE)
                .withReward(new EarningsReward(EarningsReward.TYPE_CREDITS, 45, 0));
        EarningsCenterManager manager = new EarningsCenterManager(config, claims, new TestRewards(), nativeIntegration, FIXED_CLOCK);

        EarningsEntry entry = manager.getEntries(null).stream()
                .filter(current -> current.category() == EarningsCategory.MARKETPLACE)
                .findFirst()
                .orElseThrow();
        EarningsClaimResult result = manager.claim(null, "marketplace");

        assertTrue(entry.claimable());
        assertEquals(45, entry.rewards().getFirst().amount());
        assertEquals(EarningsClaimResult.Status.SUCCESS, result.status());
        assertEquals(1, nativeIntegration.claims);
        assertTrue(claims.claims.isEmpty());
    }

    @Test
    void nativeRowsWithoutAvailableRewardsAreNotClaimable() {
        TestConfig config = enabledConfig().with("earnings.hc_payday.native.enabled", "1");
        TestNativeIntegration nativeIntegration = new TestNativeIntegration(EarningsCategory.HC_PAYDAY);
        EarningsCenterManager manager = new EarningsCenterManager(config, new TestClaims(), new TestRewards(), nativeIntegration, FIXED_CLOCK);

        EarningsEntry entry = manager.getEntries(null).stream()
                .filter(current -> current.category() == EarningsCategory.HC_PAYDAY)
                .findFirst()
                .orElseThrow();
        EarningsClaimResult result = manager.claim(null, "hc_payday");

        assertFalse(entry.claimable());
        assertEquals(EarningsClaimResult.Status.NO_REWARD, result.status());
        assertEquals(0, nativeIntegration.claims);
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

        assertEquals(EarningsClaimResult.Status.ALREADY_CLAIMED, results.get(0).status());
        assertEquals(EarningsClaimResult.Status.SUCCESS, results.get(1).status());
        assertEquals(1, rewards.granted.size());
        assertEquals(EarningsReward.TYPE_PIXELS, rewards.granted.getFirst().type());
        assertEquals(4, rewards.granted.getFirst().amount());
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

    private static class TestNativeIntegration implements EarningsCenterManager.NativeIntegration {
        private final EarningsCategory category;
        private final List<EarningsReward> rewards = new ArrayList<>();
        private int claims = 0;

        private TestNativeIntegration(EarningsCategory category) {
            this.category = category;
        }

        private TestNativeIntegration withReward(EarningsReward reward) {
            this.rewards.add(reward);
            return this;
        }

        @Override
        public boolean handles(EarningsCategory category) {
            return this.category == category;
        }

        @Override
        public boolean hasClaim(com.eu.habbo.habbohotel.users.Habbo habbo, EarningsCategory category) {
            return handles(category) && !this.rewards.isEmpty();
        }

        @Override
        public List<EarningsReward> rewards(com.eu.habbo.habbohotel.users.Habbo habbo, EarningsCategory category) {
            return handles(category) ? List.copyOf(this.rewards) : List.of();
        }

        @Override
        public boolean claim(com.eu.habbo.habbohotel.users.Habbo habbo, EarningsCategory category) {
            if (!hasClaim(habbo, category)) {
                return false;
            }

            this.claims++;
            this.rewards.clear();
            return true;
        }
    }
}
