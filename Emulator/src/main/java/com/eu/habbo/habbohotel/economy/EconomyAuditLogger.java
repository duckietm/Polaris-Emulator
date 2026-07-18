package com.eu.habbo.habbohotel.economy;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class EconomyAuditLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(EconomyAuditLogger.class);
    private static final String INSERT = """
            INSERT INTO logs_economy
                (operation_id, user_id, actor_id, operation, reason, currency_type, amount,
                 balance_before, balance_after, item_id, context)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private EconomyAuditLogger() {
    }

    public static boolean record(EconomyAuditEntry entry) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            return record(connection, entry);
        } catch (SQLException e) {
            LOGGER.error("Failed to write economy audit operation {}", entry.operationId(), e);
            return false;
        }
    }

    public static boolean record(Connection connection, EconomyAuditEntry entry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, entry.operationId());
            statement.setInt(2, entry.userId());
            if (entry.actorId() == null) statement.setNull(3, java.sql.Types.INTEGER);
            else statement.setInt(3, entry.actorId());
            statement.setString(4, entry.operation());
            statement.setString(5, entry.reason());
            statement.setInt(6, entry.currencyType());
            statement.setInt(7, entry.amount());
            statement.setInt(8, entry.balanceBefore());
            statement.setInt(9, entry.balanceAfter());
            if (entry.itemId() == null) statement.setNull(10, java.sql.Types.INTEGER);
            else statement.setInt(10, entry.itemId());
            statement.setString(11, truncate(entry.context(), 255));
            return statement.executeUpdate() == 1;
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isEmpty()) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
