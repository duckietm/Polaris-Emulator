package com.eu.habbo.habbohotel.collectibles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.CollectionState;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.CollectorScore;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.OpenReward;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CollectiblesServiceIT {
    private static final int ALICE = 920001;
    private static final int BOB = 920002;

    private static HikariDataSource dataSource;
    private final AtomicInteger roll = new AtomicInteger();
    private CollectiblesService service;

    @BeforeAll
    static void migrate() {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                fail("Docker is required in CI");
            }
            assumeTrue(false, "Docker is not available");
        }
        dataSource = TestDatabase.freshDatabase("collectibles");
        MigrationRunner.migrate(dataSource);
    }

    @AfterAll
    static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void reset() throws Exception {
        roll.set(0);
        service = new CollectiblesService(dataSource, bound -> Math.min(roll.get(), bound - 1));
        execute(
                "DELETE FROM users_collectibles",
                "DELETE FROM users_collectible_claims",
                "DELETE FROM users_collector_stats",
                "DELETE FROM collectible_products",
                "DELETE FROM collectible_collections",
                "DELETE FROM collectible_level_rewards",
                "DELETE FROM collectible_reward_boxes",
                "DELETE FROM collectible_mintables",
                "DELETE FROM collectible_mint_token_offers",
                "INSERT INTO collectible_products (product_code, product_type, item_type_id, score, rarity, collection_id, emerald_price, shop_enabled, mint_limit, sort_order) VALUES "
                        + "('bear', 1, '18', 100, 'rare', 'toys', 50, 1, 2, 1), "
                        + "('duck', 1, '19', 50, 'common', 'toys', 0, 0, -1, 2), "
                        + "('trophy', 4, 'NFT01', 10, 'epic', NULL, 0, 0, -1, 3), "
                        + "('star', 0, '20', 5, 'common', NULL, 0, 0, -1, 4), "
                        + "('relic', 1, '21', 30, 'legendary', NULL, 0, 0, 1, 5)",
                "INSERT INTO collectible_collections (collection_id, name, completion_xp, reward_product_code, bonus_product_code, bonus_start, bonus_end) VALUES "
                        + "('toys', 'Toys', 400, 'trophy', 'star', 100, 200)",
                "INSERT INTO collectible_level_rewards (min_level, product_code, claim_limit) VALUES (1, 'star', 2), (9, 'trophy', 1)",
                "INSERT INTO collectible_reward_boxes (item_id, product_code, weight) VALUES (77, 'relic', 1), (77, 'star', 1)",
                "INSERT INTO collectible_mintables (item_id, product_code, price, start_time, end_time) VALUES (88, 'relic', 3, 0, 100), (89, 'star', 1, 500, 600), (90, 'duck', 1, 0, 0)",
                "INSERT INTO collectible_mint_token_offers (product_code, silver_price, amount) VALUES ('stamps_5', 25, 5)");
    }

    @Test
    void migrationLeavesTheFeatureOff() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(
                        "SELECT `value` FROM emulator_settings WHERE `key` = 'hotel.collectibles.enabled'")) {
            assertTrue(set.next());
            assertEquals("0", set.getString(1));
        }
    }

    @Test
    void mintingNumbersSerialsUpToTheLimit() throws Exception {
        Owned first = service.mint(ALICE, "bear", "shop", 1).orElseThrow();
        Owned second = service.mint(BOB, "bear", "shop", 1).orElseThrow();
        assertEquals(1, first.serial());
        assertEquals(2, second.serial());
        assertTrue(second.assetId() > first.assetId());
        assertTrue(service.mint(ALICE, "bear", "shop", 1).isEmpty());
        assertTrue(service.mint(ALICE, "missing", "shop", 1).isEmpty());
        assertFalse(service.product("bear").orElseThrow().canMintMore());
        assertEquals(1, service.owned(ALICE).size());
        assertEquals(
                List.of("bear"),
                service.shopOffers().stream().map(p -> p.productCode()).toList());
    }

    @Test
    void completingASetAddsXpAndOpensItsClaimsOnce() throws Exception {
        service.mint(ALICE, "bear", "shop", 1);
        CollectorScore partial = service.collectorScore(ALICE, 1000);
        assertEquals(100, partial.score());
        assertEquals(1, partial.level());
        assertTrue(service.claimCollection(ALICE, "toys", false, 150).isEmpty());

        service.mint(ALICE, "duck", "admin", 1);
        CollectionState state = service.collectionStates(ALICE, 150).get(0);
        assertTrue(state.complete());
        assertEquals(150, state.score());
        assertEquals(CollectibleData.CLAIM_AVAILABLE, state.rewardClaim().status());

        Owned reward = service.claimCollection(ALICE, "toys", false, 150).orElseThrow();
        assertEquals("trophy", reward.productCode());
        assertTrue(service.claimCollection(ALICE, "toys", false, 150).isEmpty());

        assertTrue(service.claimCollection(ALICE, "toys", true, 250).isEmpty());
        assertEquals(
                "star",
                service.claimCollection(ALICE, "toys", true, 150).orElseThrow().productCode());
        assertTrue(service.claimCollection(ALICE, "toys", true, 150).isEmpty());

        CollectorScore full = service.collectorScore(ALICE, 300);
        assertEquals(100 + 50 + 400 + 10 + 5, full.score());
        assertEquals(2, full.level());
        assertEquals(full.score(), full.highestScore());
    }

    @Test
    void levelRewardsAreClaimedUpToTheirLimit() throws Exception {
        List<OpenReward> open = service.openRewards(ALICE, 1, 10);
        assertEquals(1, open.size());
        assertEquals("star", open.get(0).product().productCode());

        List<Owned> minted = service.claimLevelRewards(ALICE, 1, 10);
        assertEquals(2, minted.size());
        assertTrue(service.claimLevelRewards(ALICE, 1, 10).isEmpty());
        assertTrue(service.openRewards(ALICE, 1, 10).isEmpty());
        assertEquals(1, service.openRewards(ALICE, 9, 10).size());
    }

    @Test
    void transferMovesEveryTokenAndKeepsTheHighScore() throws Exception {
        service.mint(ALICE, "bear", "shop", 1);
        service.mint(ALICE, "duck", "shop", 1);
        int highest = service.collectorScore(ALICE, 1000).score();

        List<Owned> moved = service.transferAll(ALICE, BOB);
        assertEquals(2, moved.size());
        assertTrue(moved.stream().allMatch(token -> token.userId() == BOB));
        assertTrue(service.owned(ALICE).isEmpty());
        assertEquals(2, service.owned(BOB).size());

        CollectorScore after = service.collectorScore(ALICE, 1000);
        assertEquals(0, after.score());
        assertEquals(highest, after.highestScore());
        assertTrue(service.transferAll(BOB, BOB).isEmpty());
    }

    @Test
    void rewardBoxesDrawByWeightAndSkipSoldOutPrizes() throws Exception {
        assertTrue(service.boxPrizes(76).isEmpty());
        assertTrue(service.openBox(ALICE, 76, 1).isEmpty());

        roll.set(0);
        assertEquals("relic", service.openBox(ALICE, 77, 1).orElseThrow().productCode());
        // relic is sold out (limit 1): the same roll now falls through to the other prize
        assertEquals("star", service.openBox(ALICE, 77, 1).orElseThrow().productCode());
    }

    @Test
    void relicsCostStampsAndRespectTheirWindow() throws Exception {
        assertEquals(0, service.mintTokens(ALICE));
        assertFalse(service.addMintTokens(ALICE, -1));
        assertTrue(service.addMintTokens(ALICE, 5));
        assertEquals(1, service.mintTokenOffers().size());

        Optional<Owned> relic = service.mintRelic(ALICE, 88, 1);
        assertEquals("relic", relic.orElseThrow().productCode());
        assertEquals(2, service.mintTokens(ALICE));

        // sold out: the stamps come back
        assertTrue(service.mintRelic(ALICE, 88, 1).isEmpty());
        assertEquals(2, service.mintTokens(ALICE));

        assertTrue(service.mintRelic(ALICE, 89, 400).isEmpty());
        assertTrue(service.mintRelic(ALICE, 89, 550).isPresent());
        assertEquals(1, service.mintTokens(ALICE));
        assertTrue(service.mintRelic(ALICE, 12345, 550).isEmpty());

        // no end (0) counts as over, as in AIR: refused and the stamps kept
        assertTrue(service.mintRelic(ALICE, 90, 1).isEmpty());
        assertEquals(1, service.mintTokens(ALICE));
    }

    private static void execute(String... sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            for (String query : sql) {
                statement.execute(query);
            }
        }
    }
}
