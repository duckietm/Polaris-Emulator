package polaris.hotel.catalog;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.messages.outgoing.users.UserCreditsComposer;
import polaris.messages.outgoing.users.UserPointsComposer;

/**
 * Catalog-only facade for one atomic credits-plus-points wallet mutation.
 *
 * <p>Note: this deliberately records ledger debits and refunds without routing
 * through {@code User.giveCredits}/{@code givePoints}, so it does NOT fire
 * {@code UserCreditsEvent}/{@code UserPointsEvent}. That is intentional: a plugin
 * cancelling or reducing the debit event previously let a buyer keep the items
 * without paying. The trade-off is that plugins observing those events no longer
 * see catalog debits/refunds; catalog spend should be observed via
 * {@code UserCatalogItemPurchasedEvent} instead.
 */
public final class CatalogPaymentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogPaymentService.class);

    private CatalogPaymentService() {}

    public static boolean tryTake(User user, int credits, int pointsType, int points) {
        if (user == null || credits < 0 || points < 0) {
            return false;
        }

        try {
            LedgerWalletMutation.coordinated(user, () -> {
                WalletCommit result = mutate(
                        user, -credits, pointsType, -points, "catalog_payment_reserve", "catalog.payment.reserve");
                applyCommittedBalances(user, pointsType, result);
                return result;
            });
        } catch (IllegalArgumentException exception) {
            return false;
        } catch (SQLException exception) {
            LOGGER.error(
                    "Unable to reserve catalog payment for user {}",
                    user.getUserInfo().getId(),
                    exception);
            return false;
        }

        publish(user, credits, pointsType, -points);
        return true;
    }

    /**
     * Takes the payment on the caller's connection, inside the caller's transaction, so it is rolled back with
     * everything else the purchase wrote. The wallet in memory is not touched: once the caller has committed, it
     * passes the result to {@link #publishTaken}. The caller holds the wallet lock
     * ({@code LedgerWalletMutation.coordinated}) from here until then. An {@link IllegalArgumentException} means the
     * balance does not cover the payment.
     */
    public static TakenPayment takeWithin(Connection connection, User user, int credits, int pointsType, int points)
            throws SQLException {
        if (user == null || credits < 0 || points < 0) {
            throw new IllegalArgumentException("catalog payment cannot be negative");
        }

        WalletCommit commit = mutate(
                connection, user, -credits, pointsType, -points, "catalog_payment_reserve", "catalog.payment.reserve");
        return new TakenPayment(credits, pointsType, points, commit);
    }

    /** Applies a payment taken with {@link #takeWithin} to the wallet in memory and tells the client. */
    public static void publishTaken(User user, TakenPayment taken) {
        applyCommittedBalances(user, taken.pointsType, taken.commit);
        publish(user, taken.credits, taken.pointsType, -taken.points);
    }

    /** A payment written in a transaction that is not committed yet; only {@link #publishTaken} reads it. */
    public static final class TakenPayment {
        private final int credits;
        private final int pointsType;
        private final int points;
        private final WalletCommit commit;

        private TakenPayment(int credits, int pointsType, int points, WalletCommit commit) {
            this.credits = credits;
            this.pointsType = pointsType;
            this.points = points;
            this.commit = commit;
        }
    }

    public static void refund(User user, int credits, int pointsType, int points) {
        if (user == null || credits < 0 || points < 0) {
            throw new IllegalArgumentException("catalog refund cannot be negative");
        }

        try {
            LedgerWalletMutation.coordinated(user, () -> {
                WalletCommit result =
                        mutate(user, credits, pointsType, points, "catalog_payment_refund", "catalog.payment.refund");
                applyCommittedBalances(user, pointsType, result);
                return result;
            });
        } catch (SQLException | IllegalArgumentException exception) {
            LOGGER.error(
                    "Unable to refund catalog payment for user {}",
                    user.getUserInfo().getId(),
                    exception);
            return;
        }

        publish(user, credits, pointsType, points);
    }

    private static WalletCommit mutate(
            User user, int creditDelta, int pointsType, int pointsDelta, String operation, String reason)
            throws SQLException {
        return mutate(null, user, creditDelta, pointsType, pointsDelta, operation, reason);
    }

    /** On {@code connection} when one is given, in its transaction; otherwise in a transaction of its own. */
    private static WalletCommit mutate(
            Connection connection,
            User user,
            int creditDelta,
            int pointsType,
            int pointsDelta,
            String operation,
            String reason)
            throws SQLException {
        int userId = user.getUserInfo().getId();
        String operationId = EconomyOperationId.create("catalog-payment:" + userId);
        List<EconomyOperation> operations = new ArrayList<>(2);
        if (creditDelta != 0) {
            operations.add(new EconomyOperation(
                    operationId + ":credits",
                    userId,
                    userId,
                    operation,
                    reason,
                    EconomyLedger.CREDITS,
                    creditDelta,
                    null,
                    operationId));
        }
        if (pointsDelta != 0) {
            operations.add(new EconomyOperation(
                    operationId + ":points",
                    userId,
                    userId,
                    operation,
                    reason,
                    pointsType,
                    pointsDelta,
                    null,
                    operationId));
        }
        if (operations.isEmpty()) {
            return new WalletCommit(null, null);
        }

        List<EconomyMutationResult> results = connection == null
                ? EconomyLedger.executeBatch(operations)
                : EconomyLedger.applyBatch(connection, operations);
        int resultIndex = 0;
        EconomyMutationResult creditMutation = creditDelta == 0 ? null : results.get(resultIndex++);
        EconomyMutationResult pointsMutation = pointsDelta == 0 ? null : results.get(resultIndex);
        return new WalletCommit(creditMutation, pointsMutation);
    }

    private static void publish(User user, int credits, int pointsType, int pointsDelta) {
        if (user.getClient() != null) {
            if (credits > 0) {
                user.getClient().sendResponse(new UserCreditsComposer(user));
            }
            if (pointsDelta != 0) {
                user.getClient()
                        .sendResponse(new UserPointsComposer(
                                user.getUserInfo().getCurrencyAmount(pointsType), pointsDelta, pointsType));
            }
        }
    }

    private static void applyCommittedBalances(User user, int pointsType, WalletCommit commit) {
        if (commit.creditMutation() != null) {
            LedgerWalletMutation.applyCommitted(
                    user, EconomyLedger.CREDITS, commit.creditMutation().balanceAfter());
        }
        if (commit.pointsMutation() != null) {
            LedgerWalletMutation.applyCommitted(
                    user, pointsType, commit.pointsMutation().balanceAfter());
        }
    }

    private record WalletCommit(EconomyMutationResult creditMutation, EconomyMutationResult pointsMutation) {}
}
