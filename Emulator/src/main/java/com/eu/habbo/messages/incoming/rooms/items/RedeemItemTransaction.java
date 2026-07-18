package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

final class RedeemItemTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedeemItemTransaction.class);
    private static final String DELETE_ITEM = "DELETE FROM items WHERE id = ? AND user_id = ? LIMIT 1";

    private RedeemItemTransaction() {
    }

    static boolean commit(int itemId, int userId, int currencyType, int amount, String itemName) {
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

            EconomyLedger.apply(connection, new EconomyOperation(
                    "furniture-redeem:" + itemId,
                    userId,
                    userId,
                    "furniture_redeem",
                    "furniture.redeem",
                    currencyType,
                    amount,
                    itemId,
                    itemName));

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
