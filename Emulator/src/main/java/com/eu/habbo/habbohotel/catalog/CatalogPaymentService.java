package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserPointsComposer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catalog-only facade for one atomic credits-plus-points wallet mutation.
 *
 * <p>Note: this deliberately records ledger debits and refunds without routing
 * through {@code Habbo.giveCredits}/{@code givePoints}, so it does NOT fire
 * {@code UserCreditsEvent}/{@code UserPointsEvent}. That is intentional: a plugin
 * cancelling or reducing the debit event previously let a buyer keep the items
 * without paying. The trade-off is that plugins observing those events no longer
 * see catalog debits/refunds; catalog spend should be observed via
 * {@code UserCatalogItemPurchasedEvent} instead.
 */
public final class CatalogPaymentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogPaymentService.class);

    private CatalogPaymentService() {}

    public static boolean tryTake(Habbo habbo, int credits, int pointsType, int points) {
        if (habbo == null || credits < 0 || points < 0) {
            return false;
        }

        try {
            LedgerWalletMutation.coordinated(habbo, () -> {
                WalletCommit result = mutate(
                        habbo, -credits, pointsType, -points, "catalog_payment_reserve", "catalog.payment.reserve");
                applyCommittedBalances(habbo, pointsType, result);
                return result;
            });
        } catch (IllegalArgumentException exception) {
            return false;
        } catch (SQLException exception) {
            LOGGER.error(
                    "Unable to reserve catalog payment for user {}",
                    habbo.getHabboInfo().getId(),
                    exception);
            return false;
        }

        publish(habbo, credits, pointsType, -points);
        return true;
    }

    public static void refund(Habbo habbo, int credits, int pointsType, int points) {
        if (habbo == null || credits < 0 || points < 0) {
            throw new IllegalArgumentException("catalog refund cannot be negative");
        }

        try {
            LedgerWalletMutation.coordinated(habbo, () -> {
                WalletCommit result =
                        mutate(habbo, credits, pointsType, points, "catalog_payment_refund", "catalog.payment.refund");
                applyCommittedBalances(habbo, pointsType, result);
                return result;
            });
        } catch (SQLException | IllegalArgumentException exception) {
            LOGGER.error(
                    "Unable to refund catalog payment for user {}",
                    habbo.getHabboInfo().getId(),
                    exception);
            return;
        }

        publish(habbo, credits, pointsType, points);
    }

    private static WalletCommit mutate(
            Habbo habbo, int creditDelta, int pointsType, int pointsDelta, String operation, String reason)
            throws SQLException {
        int userId = habbo.getHabboInfo().getId();
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

        List<EconomyMutationResult> results = EconomyLedger.executeBatch(operations);
        int resultIndex = 0;
        EconomyMutationResult creditMutation = creditDelta == 0 ? null : results.get(resultIndex++);
        EconomyMutationResult pointsMutation = pointsDelta == 0 ? null : results.get(resultIndex);
        return new WalletCommit(creditMutation, pointsMutation);
    }

    private static void publish(Habbo habbo, int credits, int pointsType, int pointsDelta) {
        if (habbo.getClient() != null) {
            if (credits > 0) {
                habbo.getClient().sendResponse(new UserCreditsComposer(habbo));
            }
            if (pointsDelta != 0) {
                habbo.getClient()
                        .sendResponse(new UserPointsComposer(
                                habbo.getHabboInfo().getCurrencyAmount(pointsType), pointsDelta, pointsType));
            }
        }
    }

    private static void applyCommittedBalances(Habbo habbo, int pointsType, WalletCommit commit) {
        if (commit.creditMutation() != null) {
            LedgerWalletMutation.applyCommitted(
                    habbo, EconomyLedger.CREDITS, commit.creditMutation().balanceAfter());
        }
        if (commit.pointsMutation() != null) {
            LedgerWalletMutation.applyCommitted(
                    habbo, pointsType, commit.pointsMutation().balanceAfter());
        }
    }

    private record WalletCommit(EconomyMutationResult creditMutation, EconomyMutationResult pointsMutation) {}
}
