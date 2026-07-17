package com.eu.habbo.habbohotel.catalog.marketplace;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

final class MarketPlacePurchaseTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketPlacePurchaseTransaction.class);
    private static final String SELL_OFFER = "UPDATE marketplace_items SET state = 2, sold_timestamp = ? WHERE id = ? AND state = 1";
    private static final String TRANSFER_ITEM =
            "UPDATE items SET user_id = ? WHERE id = ? AND user_id = -1";
    private static final String CHARGE_CREDITS = "UPDATE users SET credits = credits - ? WHERE id = ? AND credits >= ?";
    private static final String CHARGE_CURRENCY = "UPDATE users_currency SET amount = amount - ? WHERE user_id = ? AND type = ? AND amount >= ?";

    private MarketPlacePurchaseTransaction() {
    }

    static boolean commit(int offerId, int itemId, int buyerId, int currencyType, int charge, int soldTimestamp) {
        if (offerId <= 0 || itemId <= 0 || buyerId <= 0 || charge <= 0) return false;

        Connection connection = null;
        try {
            connection = Emulator.getDatabase().getDataSource().getConnection();
            connection.setAutoCommit(false);

            if (!executeOfferSale(connection, offerId, soldTimestamp)
                    || !executeItemTransfer(connection, itemId, buyerId)
                    || !executeCharge(connection, buyerId, currencyType, charge)) {
                connection.rollback();
                return false;
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
            }
            LOGGER.error("Atomic marketplace purchase failed for offer {}", offerId, e);
            return false;
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(true); connection.close(); }
                catch (SQLException e) { LOGGER.warn("Failed to close marketplace transaction", e); }
            }
        }
    }

    private static boolean executeOfferSale(Connection connection, int offerId, int soldTimestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELL_OFFER)) {
            statement.setInt(1, soldTimestamp);
            statement.setInt(2, offerId);
            return statement.executeUpdate() == 1;
        }
    }

    private static boolean executeItemTransfer(Connection connection, int itemId, int buyerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TRANSFER_ITEM)) {
            statement.setInt(1, buyerId);
            statement.setInt(2, itemId);
            return statement.executeUpdate() == 1;
        }
    }

    private static boolean executeCharge(Connection connection, int buyerId, int currencyType, int charge) throws SQLException {
        if (currencyType < 0) {
            try (PreparedStatement statement = connection.prepareStatement(CHARGE_CREDITS)) {
                statement.setInt(1, charge);
                statement.setInt(2, buyerId);
                statement.setInt(3, charge);
                return statement.executeUpdate() == 1;
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(CHARGE_CURRENCY)) {
            statement.setInt(1, charge);
            statement.setInt(2, buyerId);
            statement.setInt(3, currencyType);
            statement.setInt(4, charge);
            return statement.executeUpdate() == 1;
        }
    }
}
