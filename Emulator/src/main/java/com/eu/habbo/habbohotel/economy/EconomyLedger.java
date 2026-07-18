package com.eu.habbo.habbohotel.economy;

import com.eu.habbo.Emulator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class EconomyLedger {
    public static final int CREDITS = -1;
    private static final String LOCK_USER = "SELECT credits FROM users WHERE id = ? FOR UPDATE";
    private static final String READ_CURRENCY = "SELECT amount FROM users_currency WHERE user_id = ? AND type = ? FOR UPDATE";
    private static final String UPDATE_CREDITS = "UPDATE users SET credits = ? WHERE id = ?";
    private static final String UPSERT_CURRENCY = """
            INSERT INTO users_currency (user_id, type, amount) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE amount = VALUES(amount)
            """;
    private static final String READ_OPERATION = """
            SELECT user_id, currency_type, amount, balance_before, balance_after
            FROM logs_economy WHERE operation_id = ?
            """;

    private EconomyLedger() {
    }

    public static EconomyMutationResult execute(EconomyOperation operation) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                EconomyMutationResult result = apply(connection, operation);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    public static EconomyMutationResult apply(Connection connection, EconomyOperation operation) throws SQLException {
        int balanceBefore = lockBalance(connection, operation.userId(), operation.currencyType());
        EconomyMutationResult existing = existingResult(connection, operation);
        if (existing != null) return existing;

        int balanceAfter = checkedBalance(balanceBefore, operation.delta());
        persistBalance(connection, operation.userId(), operation.currencyType(), balanceAfter);
        EconomyAuditLogger.record(connection, EconomyAuditEntry.from(operation, balanceBefore, balanceAfter));
        return new EconomyMutationResult(balanceBefore, balanceAfter, true);
    }

    public static int checkedBalance(int balanceBefore, int delta) {
        long balanceAfter = (long) balanceBefore + delta;
        if (balanceBefore < 0 || balanceAfter < 0 || balanceAfter > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("economy mutation would produce an invalid balance");
        }
        return (int) balanceAfter;
    }

    private static int lockBalance(Connection connection, int userId, int currencyType) throws SQLException {
        int credits;
        try (PreparedStatement statement = connection.prepareStatement(LOCK_USER)) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Unknown economy user " + userId);
                credits = result.getInt("credits");
            }
        }
        if (currencyType == CREDITS) return credits;

        try (PreparedStatement statement = connection.prepareStatement(READ_CURRENCY)) {
            statement.setInt(1, userId);
            statement.setInt(2, currencyType);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("amount") : 0;
            }
        }
    }

    private static EconomyMutationResult existingResult(Connection connection, EconomyOperation operation)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(READ_OPERATION)) {
            statement.setString(1, operation.operationId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                if (result.getInt("user_id") != operation.userId()
                        || result.getInt("currency_type") != operation.currencyType()
                        || result.getInt("amount") != operation.delta()) {
                    throw new SQLException("Economy operation id reused with different payload: "
                            + operation.operationId());
                }
                return new EconomyMutationResult(
                        result.getInt("balance_before"), result.getInt("balance_after"), false);
            }
        }
    }

    private static void persistBalance(Connection connection, int userId, int currencyType, int balance)
            throws SQLException {
        if (currencyType == CREDITS) {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_CREDITS)) {
                statement.setInt(1, balance);
                statement.setInt(2, userId);
                if (statement.executeUpdate() != 1) throw new SQLException("Unable to update credits for " + userId);
            }
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(UPSERT_CURRENCY)) {
            statement.setInt(1, userId);
            statement.setInt(2, currencyType);
            statement.setInt(3, balance);
            statement.executeUpdate();
        }
    }
}
