package com.eu.habbo.habbohotel.economy;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EconomyLedgerIT {
    @Test
    void walletBatchCommitsTogetherAndRollsBackAcrossCurrencyTypes()
            throws Exception {
        requireDocker();
        try (HikariDataSource dataSource =
                     TestDatabase.freshDatabase("economy_ledger")) {
            MigrationRunner.migrate(dataSource);
            seedWallet(dataSource);

            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                List<EconomyMutationResult> results =
                        EconomyLedger.applyBatch(connection, List.of(
                                operation("commit:credits", EconomyLedger.CREDITS, -100),
                                operation("commit:points", 5, -20)));
                connection.commit();

                assertEquals(900, results.get(0).balanceAfter());
                assertEquals(180, results.get(1).balanceAfter());
            }
            assertEquals(900, value(dataSource,
                    "SELECT credits FROM users WHERE id = 910001"));
            assertEquals(180, value(dataSource, """
                    SELECT amount FROM users_currency
                    WHERE user_id = 910001 AND type = 5
                    """));
            assertEquals(2, value(dataSource, """
                    SELECT COUNT(*) FROM logs_economy
                    WHERE user_id = 910001
                    """));

            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                assertThrows(IllegalArgumentException.class, () ->
                        EconomyLedger.applyBatch(connection, List.of(
                                operation("rollback:points", 5, -10),
                                operation("rollback:credits", EconomyLedger.CREDITS, -1000))));
                connection.rollback();
            }
            assertEquals(900, value(dataSource,
                    "SELECT credits FROM users WHERE id = 910001"));
            assertEquals(180, value(dataSource, """
                    SELECT amount FROM users_currency
                    WHERE user_id = 910001 AND type = 5
                    """));
            assertEquals(0, value(dataSource, """
                    SELECT COUNT(*) FROM logs_economy
                    WHERE operation_id LIKE 'rollback:%'
                    """));
        }
    }

    private static EconomyOperation operation(
            String operationId,
            int currencyType,
            int delta) {
        return new EconomyOperation(
                operationId,
                910001,
                910001,
                "integration_test",
                "test.economy.ledger",
                currencyType,
                delta,
                null,
                "");
    }

    private static void seedWallet(HikariDataSource dataSource)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users
                        (id, username, password, ip_register, ip_current, credits)
                    VALUES
                        (910001, 'ledger_it', '!', '127.0.0.1', '127.0.0.1', 1000)
                    """);
            statement.executeUpdate("""
                    INSERT INTO users_currency (user_id, type, amount)
                    VALUES (910001, 5, 200)
                    """);
        }
    }

    private static int value(HikariDataSource dataSource, String sql)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void requireDocker() {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new AssertionError(
                        "Docker/Testcontainers is required in CI");
            }
            assumeTrue(false,
                    "Docker/Testcontainers not available - skipping DB integration test");
        }
    }
}
