package polaris.hotel.catalog.marketplace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.economy.EconomyOperation;

/**
 * Pays a seller what their sold offers earned. Every offer is flagged as paid out and the seller's wallet is credited
 * in one transaction: the flags used to be written one by one before the credit, so a crash or a refused credit in
 * between left offers paid out with nothing paid, and a failed release kept them that way for good.
 */
final class MarketPlacePayoutTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketPlacePayoutTransaction.class);

    /**
     * Keyed on the seller and the sold state, so two sessions of the same seller cannot both take the same offer, and
     * an offer that is not this seller's, or not sold, is never claimable at all.
     */
    static final String CLAIM_OFFER =
            "UPDATE marketplace_items SET seller_claimed = 1 WHERE id = ? AND user_id = ? AND state = ? AND seller_claimed = 0";

    private MarketPlacePayoutTransaction() {}

    /**
     * Runs on the caller's connection, which it hands back in auto-commit mode; the caller closes it.
     *
     * @return the wallet mutation, or null when nothing changed: an offer was already paid out (another session got
     *     it first), or the database refused
     */
    static EconomyMutationResult commit(
            Connection connection, int sellerId, List<Integer> offerIds, int currencyType, int amount) {
        if (sellerId <= 0 || offerIds.isEmpty() || amount <= 0) return null;

        try {
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(CLAIM_OFFER)) {
                for (int offerId : offerIds) {
                    statement.setInt(1, offerId);
                    statement.setInt(2, sellerId);
                    statement.setInt(3, MarketPlaceState.SOLD.getState());
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return null;
                    }
                }
            }

            EconomyMutationResult walletMutation = EconomyLedger.apply(
                    connection,
                    new EconomyOperation(
                            "marketplace:payout:" + sellerId + ":" + offerIds.get(0) + ":" + offerIds.size(),
                            sellerId,
                            sellerId,
                            "marketplace_payout",
                            "marketplace.payout",
                            currencyType < 0 ? EconomyLedger.CREDITS : currencyType,
                            amount,
                            null,
                            "offers=" + offerIds));

            connection.commit();
            return walletMutation;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            LOGGER.error("Marketplace payout failed for seller {}", sellerId, e);
            return null;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                LOGGER.warn("Failed to restore auto-commit after a marketplace payout", e);
            }
        }
    }
}
