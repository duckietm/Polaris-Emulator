package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.plugin.events.furniture.FurnitureRedeemedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

final class RedeemItemTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedeemItemTransaction.class);
    private static final String DELETE_ITEM = "DELETE FROM items WHERE id = ? AND user_id = ? LIMIT 1";
    private static final String ADD_CREDITS = "UPDATE users SET credits = credits + ? WHERE id = ? LIMIT 1";
    private static final String ADD_CURRENCY = """
            INSERT INTO users_currency (user_id, type, amount) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE amount = amount + ?
            """;

    private RedeemItemTransaction() {
    }

    static boolean commit(int itemId, int userId, int currencyType, int amount) {
        if (itemId <= 0 || userId <= 0 || amount <= 0) return false;

        Connection connection = null;
        try {
            connection = Emulator.getDatabase().getDataSource().getConnection();
            connection.setAutoCommit(false);

            try (PreparedStatement delete = connection.prepareStatement(DELETE_ITEM)) {
                delete.setInt(1, itemId);
                delete.setInt(2, userId);
                if (delete.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }

            if (currencyType == FurnitureRedeemedEvent.CREDITS) {
                try (PreparedStatement update = connection.prepareStatement(ADD_CREDITS)) {
                    update.setInt(1, amount);
                    update.setInt(2, userId);
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
            } else {
                try (PreparedStatement update = connection.prepareStatement(ADD_CURRENCY)) {
                    update.setInt(1, userId);
                    update.setInt(2, currencyType);
                    update.setInt(3, amount);
                    update.setInt(4, amount);
                    update.executeUpdate();
                }
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
            }
            LOGGER.error("Atomic redemption failed for item {} and user {}", itemId, userId, e);
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (SQLException e) {
                    LOGGER.warn("Failed to close redemption transaction connection", e);
                }
            }
        }
    }
}
