package com.eu.habbo.habbohotel.collectibles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.WardrobeOutfit;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.WornOutfit;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CollectibleAvatarsIT {
    private static final int ALICE = 930001;
    private static final int BOB = 930002;
    private static final String PUNK = "hd-180-1.hr-100-61.ch-210-66";
    private static final String QUEEN = "hd-600-1.hr-515-33.ch-635-70";
    private static final String OWN = "hd-190-1.hr-110-42";

    private static HikariDataSource dataSource;
    private CollectiblesService service;
    private CollectibleAvatars avatars;

    @BeforeAll
    static void migrate() {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                fail("Docker is required in CI");
            }
            assumeTrue(false, "Docker is not available");
        }
        dataSource = TestDatabase.freshDatabase("collectible_avatars");
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
        service = new CollectiblesService(dataSource);
        avatars = service.avatars();
        execute(
                "DELETE FROM users_collectibles",
                "DELETE FROM users_collectible_outfits",
                "DELETE FROM collectible_products",
                "DELETE FROM collectible_avatar_outfits",
                "INSERT INTO collectible_products (product_code, product_type, item_type_id, score, rarity, mint_limit) VALUES "
                        + "('punk', 11, '', 10, 'rare', -1), ('queen', 11, '', 10, 'epic', -1), ('bear', 1, '18', 5, 'common', -1)",
                "INSERT INTO collectible_avatar_outfits (product_code, figure, gender, contract_key) VALUES "
                        + "('punk', '" + PUNK + "', 'M', 'habbo:avatar'), ('queen', '" + QUEEN
                        + "', 'f', 'habbo:avatar_genesis')");
    }

    @Test
    void migrationAddsTheSettingAndTheStaffPermission() throws Exception {
        assertEquals(
                "1",
                single("SELECT `value` FROM emulator_settings WHERE `key` = 'hotel.collectibles.avatars.enabled'"));
        assertEquals(
                "1",
                single("SELECT COUNT(*) FROM permission_definitions WHERE permission_key = 'cmd_give_collectible'"));
    }

    @Test
    void theWardrobeListsOnlyOwnedLooks() throws Exception {
        Owned punk = service.mint(ALICE, "punk", "staff", 1).orElseThrow();
        service.mint(ALICE, "bear", "staff", 1).orElseThrow();
        service.mint(BOB, "queen", "staff", 1).orElseThrow();

        List<WardrobeOutfit> wardrobe = avatars.wardrobe(ALICE);
        assertEquals(1, wardrobe.size());
        assertEquals(punk.assetId(), wardrobe.get(0).token().assetId());
        assertEquals(PUNK, wardrobe.get(0).outfit().figure());
        assertEquals("M", wardrobe.get(0).outfit().gender());
        assertEquals("F", avatars.wardrobe(BOB).get(0).outfit().gender());
    }

    @Test
    void wearingKeepsTheLookFromBeforeTheFirstCollectible() throws Exception {
        Owned punk = service.mint(ALICE, "punk", "staff", 1).orElseThrow();
        Owned queen = service.mint(ALICE, "queen", "staff", 1).orElseThrow();

        assertTrue(avatars.wear(ALICE, punk.assetId(), OWN, "m", 10).isPresent());
        WornOutfit worn = avatars.worn(ALICE, PUNK).orElseThrow();
        assertEquals(punk.assetId(), worn.assetId());
        assertEquals(OWN, worn.fallbackFigure());
        assertEquals("M", worn.fallbackGender());

        assertTrue(avatars.wear(ALICE, queen.assetId(), PUNK, "M", 11).isPresent());
        worn = avatars.worn(ALICE, QUEEN).orElseThrow();
        assertEquals(queen.assetId(), worn.assetId());
        assertEquals(OWN, worn.fallbackFigure());
    }

    @Test
    void anotherLookTakesTheCollectibleOff() throws Exception {
        Owned punk = service.mint(ALICE, "punk", "staff", 1).orElseThrow();
        avatars.wear(ALICE, punk.assetId(), OWN, "M", 10).orElseThrow();

        assertTrue(avatars.worn(ALICE, OWN).isEmpty());

        // wearing it again after a look change remembers the new look to go back to
        avatars.wear(ALICE, punk.assetId(), "hd-185-1", "M", 12).orElseThrow();
        assertEquals("hd-185-1", avatars.worn(ALICE, PUNK).orElseThrow().fallbackFigure());
    }

    @Test
    void onlyTheOwnerCanWearAToken() throws Exception {
        Owned queen = service.mint(BOB, "queen", "staff", 1).orElseThrow();
        Owned bear = service.mint(ALICE, "bear", "staff", 1).orElseThrow();

        assertTrue(avatars.wear(ALICE, queen.assetId(), OWN, "M", 10).isEmpty());
        assertTrue(avatars.wear(ALICE, bear.assetId(), OWN, "M", 10).isEmpty());
        assertEquals("0", single("SELECT COUNT(*) FROM users_collectible_outfits"));
    }

    @Test
    void givingTheTokenAwayTakesItOff() throws Exception {
        Owned punk = service.mint(ALICE, "punk", "staff", 1).orElseThrow();
        avatars.wear(ALICE, punk.assetId(), OWN, "M", 10).orElseThrow();

        service.transferAll(ALICE, BOB);

        assertTrue(avatars.worn(ALICE, PUNK).isEmpty());
        assertTrue(avatars.wardrobe(ALICE).isEmpty());
        assertEquals(1, avatars.wardrobe(BOB).size());
    }

    private static String single(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(sql)) {
            assertTrue(set.next());
            return set.getString(1);
        }
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
