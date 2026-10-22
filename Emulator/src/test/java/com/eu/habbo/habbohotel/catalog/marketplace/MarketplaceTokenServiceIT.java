package com.eu.habbo.habbohotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketplaceTokenServiceIT {
    private static final int USER = 910301;

    private static HikariDataSource dataSource;
    private final AtomicReference<MarketplaceTokenService.Settings> settings =
            new AtomicReference<>(new MarketplaceTokenService.Settings(true, 3, 5));
    private MarketplaceTokenService service;

    @BeforeAll
    static void migrate() {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                fail("Docker is required in CI");
            }
            assumeTrue(false, "Docker is not available");
        }
        dataSource = TestDatabase.freshDatabase("marketplace_tokens");
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
        service = new MarketplaceTokenService(dataSource, settings::get);
        execute(
                "DELETE FROM users_marketplace_tokens",
                "DELETE FROM users WHERE id = " + USER,
                "INSERT INTO users (id, username, password, ip_register, ip_current, credits) VALUES (" + USER
                        + ", 'mp_tokens_it', '!', '127.0.0.1', '127.0.0.1', 7)");
    }

    @Test
    void migrationLeavesTokensOffWithTheOfficialBatch() throws Exception {
        assertEquals("0", setting(MarketplaceTokenService.ENABLED_SETTING));
        assertEquals("1", setting(MarketplaceTokenService.PRICE_SETTING));
        assertEquals("5", setting(MarketplaceTokenService.AMOUNT_SETTING));
    }

    @Test
    void buyingChargesCreditsAndAddsOneBatch() throws Exception {
        assertEquals(0, service.tokens(USER));

        MarketplaceTokenService.Purchase purchase = service.buy(USER);

        assertEquals(4, purchase.creditsAfter());
        assertEquals(5, purchase.tokens());
        assertEquals(5, service.tokens(USER));
        assertEquals(4, credits());

        assertEquals(10, service.buy(USER).tokens());
        assertEquals(1, credits());
    }

    @Test
    void aUserWhoCannotPayGetsNoTokens() throws Exception {
        service.buy(USER);
        service.buy(USER);

        assertThrows(IllegalArgumentException.class, () -> service.buy(USER));
        assertEquals(10, service.tokens(USER));
        assertEquals(1, credits());
    }

    @Test
    void aFreeBatchChargesNothing() throws Exception {
        settings.set(new MarketplaceTokenService.Settings(true, 0, 2));

        MarketplaceTokenService.Purchase purchase = service.buy(USER);

        assertEquals(-1, purchase.creditsAfter());
        assertEquals(2, purchase.tokens());
        assertEquals(7, credits());
    }

    @Test
    void spendingIsAllOrNothingAndRefundsComeBack() throws Exception {
        assertFalse(service.spend(USER, 1));

        service.buy(USER);
        assertTrue(service.spend(USER, 3));
        assertFalse(service.spend(USER, 3));
        assertEquals(2, service.tokens(USER));

        service.refund(USER, 1);
        assertEquals(3, service.tokens(USER));
        assertTrue(service.spend(USER, 3));
        assertEquals(0, service.tokens(USER));
    }

    private static int credits() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT credits FROM users WHERE id = " + USER)) {
            assertTrue(set.next());
            return set.getInt(1);
        }
    }

    private static String setting(String key) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet set =
                        statement.executeQuery("SELECT `value` FROM emulator_settings WHERE `key` = '" + key + "'")) {
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
