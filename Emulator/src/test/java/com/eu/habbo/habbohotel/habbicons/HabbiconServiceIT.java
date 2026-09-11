package com.eu.habbo.habbohotel.habbicons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.habbicons.HabbiconService.Action;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HabbiconServiceIT {
    private static HikariDataSource dataSource;
    private HabbiconService service;

    @BeforeAll
    static void migrate() {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                fail("Docker is required in CI");
            }
            assumeTrue(false, "Docker is not available");
        }
        dataSource = TestDatabase.freshDatabase("habbicons");
        MigrationRunner.migrate(dataSource);
    }

    @AfterAll
    static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void seed() throws Exception {
        service = new HabbiconService(dataSource);
        execute(
                "DELETE FROM users_habbicons",
                "TRUNCATE TABLE logs_economy",
                "DELETE FROM users_currency",
                "DELETE FROM users WHERE id = 910001",
                "INSERT INTO users (id, username, password, ip_register, ip_current, credits) VALUES (910001, 'habbicon_it', '!', '127.0.0.1', '127.0.0.1', 100)",
                "INSERT INTO users_currency (user_id, type, amount) VALUES (910001, 5, 20)",
                "UPDATE habbicons SET cost_credits = 10, cost_points = 2, points_type = 5, available = TRUE, default_owned = (collection_id = 7)",
                "UPDATE habbicon_collections SET cost_credits = 30, cost_points = 5, points_type = 5");
    }

    @Test
    void starterOwnershipAndUnavailableItemsComeFromServerConfiguration() throws Exception {
        assertTrue(service.load(910001).requireItem(28).owned());
        assertEquals(7, service.load(910001).requireItem(28).collectionId());
        assertEquals(8, service.load(910001).requireItem(39).collectionId());
        assertEquals(5, service.load(910001).requireItem(50).collectionId());
        assertEquals(6, service.load(910001).requireItem(61).collectionId());
        assertFalse(service.load(910001).requireItem(61).owned());
        assertFalse(service.use(910001, 61));
        assertFalse(service.use(910001, Integer.MAX_VALUE));
        execute("UPDATE habbicons SET available = FALSE WHERE id = 61");
        assertEquals(
                HabbiconService.UNAVAILABLE,
                service.load(910001).requireItem(61).state());
        assertEquals(
                1,
                assertThrows(HabbiconService.Rejected.class, () -> service.change(910001, Action.BUY, 61))
                        .code());
        assertEquals(
                1,
                assertThrows(HabbiconService.Rejected.class, () -> service.change(910001, Action.BUY, 999))
                        .code());
        assertEquals(
                4,
                assertThrows(HabbiconService.Rejected.class, () -> service.change(910001, Action.CLAIM, 71))
                        .code());
    }

    @Test
    void freeAvailableIconsAreClaimableWithoutInventingAPrice() throws Exception {
        execute("UPDATE habbicons SET cost_credits = 0, cost_points = 0 WHERE id = 61");
        assertEquals(
                HabbiconService.CLAIMABLE, service.load(910001).requireItem(61).state());
        assertFalse(service.use(910001, 61));
        service.change(910001, Action.CLAIM, 61);
        assertTrue(service.use(910001, 61));
        assertEquals(100, scalar("SELECT credits FROM users WHERE id = 910001"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM logs_economy WHERE user_id = 910001"));
    }

    @Test
    void purchasePersistsBothCurrenciesOwnershipFavoritesAndUnseenAcrossSessions() throws Exception {
        service.change(910001, Action.BUY, 61);
        assertEquals(90, scalar("SELECT credits FROM users WHERE id = 910001"));
        assertEquals(18, scalar("SELECT amount FROM users_currency WHERE user_id = 910001 AND type = 5"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM logs_economy WHERE user_id = 910001"));
        assertEquals(List.of(61), service.load(910001).unseen());
        service.change(910001, Action.FAVORITE, 61);
        assertEquals(
                HabbiconService.FAVORITE,
                new HabbiconService(dataSource).load(910001).requireItem(61).state());
        service.change(910001, Action.UNFAVORITE, 61);
        service.clearUnseen(910001, List.of(28));
        assertEquals(List.of(61), service.load(910001).unseen());
        service.clearUnseen(910001, List.of(61));
        assertTrue(service.load(910001).unseen().isEmpty());
        assertEquals(
                4,
                assertThrows(HabbiconService.Rejected.class, () -> service.change(910001, Action.BUY, 61))
                        .code());
        assertEquals(90, scalar("SELECT credits FROM users WHERE id = 910001"));
    }

    @Test
    void insufficientSecondCurrencyRollsBackCreditsLedgerAndOwnership() throws Exception {
        execute("UPDATE habbicons SET cost_points = 21 WHERE id = 61");
        assertEquals(
                3,
                assertThrows(HabbiconService.Rejected.class, () -> service.change(910001, Action.BUY, 61))
                        .code());
        assertEquals(100, scalar("SELECT credits FROM users WHERE id = 910001"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM logs_economy WHERE user_id = 910001"));
        assertFalse(service.load(910001).requireItem(61).owned());
        execute("UPDATE habbicons SET cost_credits = 101 WHERE id = 61");
        assertEquals(
                2,
                assertThrows(HabbiconService.Rejected.class, () -> service.change(910001, Action.BUY, 61))
                        .code());
    }

    @Test
    void setPurchaseGrantsMissingMembersAndUnlocksAnExactlyOnceRewardClaim() throws Exception {
        service.change(910001, Action.BUY, 61);
        service.change(910001, Action.FAVORITE, 61);
        HabbiconService.Change change = service.change(910001, Action.BUY_COLLECTION, 6);
        assertTrue(change.snapshot().collections().stream()
                .filter(collection -> collection.id() == 6)
                .findFirst()
                .orElseThrow()
                .completed());
        assertEquals(HabbiconService.FAVORITE, change.snapshot().requireItem(61).state());
        assertEquals(
                HabbiconService.CLAIMABLE, change.snapshot().requireItem(71).state());
        assertFalse(service.use(910001, 71));
        service.change(910001, Action.CLAIM, 71);
        assertTrue(service.use(910001, 71));
        assertEquals(60, scalar("SELECT credits FROM users WHERE id = 910001"));
        assertEquals(
                4,
                assertThrows(HabbiconService.Rejected.class, () -> service.change(910001, Action.CLAIM, 71))
                        .code());
        assertEquals(
                4,
                assertThrows(HabbiconService.Rejected.class, () -> service.change(910001, Action.BUY_COLLECTION, 6))
                        .code());
        service.clearUnseen(910001, List.of());
        assertTrue(service.load(910001).unseen().isEmpty());
    }

    @Test
    void recentUseIsDistinctNewestFirstLimitedToTenAndPreservesFavorites() throws Exception {
        service.change(910001, Action.BUY_COLLECTION, 6);
        service.change(910001, Action.FAVORITE, 61);
        for (int id = 61; id <= 70; id++) {
            assertTrue(service.use(910001, id));
        }
        assertTrue(service.use(910001, 28));
        assertTrue(service.use(910001, 61));
        assertEquals(
                List.of(61, 28, 70, 69, 68, 67, 66, 65, 64, 63),
                new HabbiconService(dataSource).load(910001).recent());
        assertEquals(
                HabbiconService.FAVORITE, service.load(910001).requireItem(61).state());
    }

    @Test
    void concurrentDuplicatePurchasesChargeExactlyOnce() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var outcomes = executor.<Boolean>invokeAll(List.of(() -> buyOnce(), () -> buyOnce()));
            assertEquals(1, (outcomes.get(0).get() ? 1 : 0) + (outcomes.get(1).get() ? 1 : 0));
        }
        assertEquals(90, scalar("SELECT credits FROM users WHERE id = 910001"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM logs_economy WHERE user_id = 910001"));
    }

    private Boolean buyOnce() throws Exception {
        try {
            service.change(910001, Action.BUY, 61);
            return true;
        } catch (HabbiconService.Rejected rejected) {
            assertEquals(4, rejected.code());
            return false;
        }
    }

    private static void execute(String... commands) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            for (String command : commands) {
                statement.executeUpdate(command);
            }
        }
    }

    private static int scalar(String query) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
