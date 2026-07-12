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
            INSERT IGNORE INTO logs_economy
                (operation_id, user_id, operation, currency_type, amount, balance_before, balance_after, item_id, context)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private EconomyAuditLogger() {
    }

    public static boolean record(EconomyAuditEntry entry) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, entry.operationId());
            statement.setInt(2, entry.userId());
            statement.setString(3, entry.operation());
            statement.setInt(4, entry.currencyType());
            statement.setInt(5, entry.amount());
            statement.setInt(6, entry.balanceBefore());
            statement.setInt(7, entry.balanceAfter());
            if (entry.itemId() == null) statement.setNull(8, java.sql.Types.INTEGER);
            else statement.setInt(8, entry.itemId());
            statement.setString(9, truncate(entry.context(), 255));
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            LOGGER.error("Failed to write economy audit operation {}", entry.operationId(), e);
            return false;
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isEmpty()) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
