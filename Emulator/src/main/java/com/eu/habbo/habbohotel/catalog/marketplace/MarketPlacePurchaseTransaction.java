package com.eu.habbo.habbohotel.catalog.marketplace;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MarketPlacePurchaseTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketPlacePurchaseTransaction.class);
    private static final String SELL_OFFER =
            "UPDATE marketplace_items SET state = 2, sold_timestamp = ? WHERE id = ? AND state = 1";
    private static final String TRANSFER_ITEM = "UPDATE items SET user_id = ? WHERE id = ? AND user_id = -1";

    private MarketPlacePurchaseTransaction() {}

    static EconomyMutationResult commit(
            int offerId, int itemId, int buyerId, int currencyType, int charge, int soldTimestamp) {
        if (offerId <= 0 || itemId <= 0 || buyerId <= 0 || charge <= 0) return null;

        Connection connection = null;
        try {
            connection = Emulator.getDatabase().getDataSource().getConnection();
            connection.setAutoCommit(false);

            if (!executeOfferSale(connection, offerId, soldTimestamp)
                    || !executeItemTransfer(connection, itemId, buyerId)) {
                connection.rollback();
                return null;
            }

            EconomyMutationResult walletMutation = EconomyLedger.apply(
                    connection,
                    new EconomyOperation(
                            "marketplace:offer:" + offerId + ":buyer",
                            buyerId,
                            buyerId,
                            "marketplace_purchase",
                            "catalog.marketplace.buy",
                            currencyType < 0 ? EconomyLedger.CREDITS : currencyType,
                            -charge,
                            itemId,
                            "offerId=" + offerId));

            connection.commit();
            return walletMutation;
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
            }
            LOGGER.error("Atomic marketplace purchase failed for offer {}", offerId, e);
            return null;
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (SQLException e) {
                    LOGGER.warn("Failed to close marketplace transaction", e);
                }
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
}
