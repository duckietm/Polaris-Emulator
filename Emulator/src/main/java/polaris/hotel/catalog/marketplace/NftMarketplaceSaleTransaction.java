package polaris.hotel.catalog.marketplace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.economy.EconomyOperation;

/**
 * One collectible sale in one transaction: the offer is sold and marked paid out, the furni goes to the buyer, the
 * buyer pays the price and the seller receives the payout. Either everything happens or nothing does.
 */
final class NftMarketplaceSaleTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(NftMarketplaceSaleTransaction.class);

    static final String SELL_OFFER =
            "UPDATE marketplace_items SET state = 2, sold_timestamp = ?, seller_claimed = 1 WHERE id = ? AND state = 1 AND nft = 1";
    static final String TRANSFER_ITEM = "UPDATE items SET user_id = ? WHERE id = ? AND user_id = -1";

    private NftMarketplaceSaleTransaction() {}

    record Sale(
            int offerId,
            int itemId,
            int buyerId,
            int sellerId,
            int currencyType,
            int price,
            int payout,
            int soldTimestamp) {}

    /** The wallet results: the buyer's, and the seller's (null when the payout was 0). */
    record Result(EconomyMutationResult buyer, EconomyMutationResult seller) {}

    static Result commit(DataSource dataSource, Sale sale) {
        if (sale.offerId() <= 0
                || sale.itemId() <= 0
                || sale.buyerId() <= 0
                || sale.sellerId() <= 0
                || sale.buyerId() == sale.sellerId()
                || sale.price() <= 0
                || sale.payout() < 0
                || sale.payout() > sale.price()) {
            return null;
        }

        try (Connection connection = dataSource.getConnection()) {
            return commit(connection, sale);
        } catch (SQLException e) {
            LOGGER.error("Collectible sale of offer {} could not reach the database", sale.offerId(), e);
            return null;
        }
    }

    /** Runs on the caller's connection and hands it back in auto-commit mode. */
    static Result commit(Connection connection, Sale sale) {
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(SELL_OFFER)) {
                statement.setInt(1, sale.soldTimestamp());
                statement.setInt(2, sale.offerId());
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return null;
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(TRANSFER_ITEM)) {
                statement.setInt(1, sale.buyerId());
                statement.setInt(2, sale.itemId());
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return null;
                }
            }

            EconomyMutationResult buyer = EconomyLedger.apply(
                    connection,
                    new EconomyOperation(
                            "marketplace:nft:" + sale.offerId() + ":buyer",
                            sale.buyerId(),
                            sale.buyerId(),
                            "marketplace_purchase",
                            "catalog.marketplace.nft.buy",
                            sale.currencyType(),
                            -sale.price(),
                            sale.itemId(),
                            "offerId=" + sale.offerId()));

            EconomyMutationResult seller = null;
            if (sale.payout() > 0) {
                seller = EconomyLedger.apply(
                        connection,
                        new EconomyOperation(
                                "marketplace:nft:" + sale.offerId() + ":seller",
                                sale.sellerId(),
                                sale.buyerId(),
                                "marketplace_payout",
                                "marketplace.nft.payout",
                                sale.currencyType(),
                                sale.payout(),
                                sale.itemId(),
                                "offerId=" + sale.offerId() + ";fee=" + (sale.price() - sale.payout())));
            }

            connection.commit();
            return new Result(buyer, seller);
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            LOGGER.error("Collectible sale of offer {} failed", sale.offerId(), e);
            return null;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                LOGGER.warn("Failed to restore auto-commit after a collectible sale", e);
            }
        }
    }
}
