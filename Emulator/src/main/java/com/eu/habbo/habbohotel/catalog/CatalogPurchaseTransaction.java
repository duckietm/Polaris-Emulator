package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;

import java.sql.Connection;
import java.sql.SQLException;

final class CatalogPurchaseTransaction {
    private CatalogPurchaseTransaction() {
    }

    static <T> T execute(
            Habbo habbo,
            String operationId,
            PurchaseWork<T> work) throws SQLException {
        return LedgerWalletMutation.coordinated(habbo, () -> {
            Commit<PreparedPurchase<T>> commit = commit(
                    habbo.getHabboInfo().getId(),
                    operationId,
                    work);
            if (commit.creditMutation() != null) {
                LedgerWalletMutation.applyCommitted(
                        habbo,
                        EconomyLedger.CREDITS,
                        commit.creditMutation().balanceAfter());
            }
            if (commit.pointsMutation() != null) {
                LedgerWalletMutation.applyCommitted(
                        habbo,
                        commit.value().pointsType(),
                        commit.pointsMutation().balanceAfter());
            }
            return commit.value().value();
        });
    }

    private static <T> Commit<PreparedPurchase<T>> commit(
            int userId,
            String operationId,
            PurchaseWork<T> work) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                PreparedPurchase<T> purchase = work.persist(connection);
                EconomyMutationResult creditMutation =
                        chargeCredits(connection, operationId, userId, purchase.credits());
                EconomyMutationResult pointsMutation =
                        chargePoints(connection, operationId, userId, purchase.pointsType(), purchase.points());
                connection.commit();
                return new Commit<>(purchase, creditMutation, pointsMutation);
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                if (exception instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("Unable to persist catalog purchase", exception);
            }
        }
    }

    private static EconomyMutationResult chargeCredits(
            Connection connection,
            String operationId,
            int userId,
            int credits)
            throws SQLException {
        if (credits == 0) return null;
        return EconomyLedger.apply(connection, new EconomyOperation(
                operationId + ":credits", userId, userId, "catalog_purchase", "catalog.purchase",
                EconomyLedger.CREDITS, -credits, null, operationId));
    }

    private static EconomyMutationResult chargePoints(
            Connection connection,
            String operationId,
            int userId,
            int pointsType,
            int points)
            throws SQLException {
        if (points == 0) return null;
        return EconomyLedger.apply(connection, new EconomyOperation(
                operationId + ":points", userId, userId, "catalog_purchase", "catalog.purchase",
                pointsType, -points, null, operationId));
    }

    @FunctionalInterface
    interface PurchaseWork<T> {
        PreparedPurchase<T> persist(Connection connection) throws Exception;
    }

    record PreparedPurchase<T>(T value, int credits, int points, int pointsType) {
        PreparedPurchase {
            if (credits < 0 || points < 0) throw new IllegalArgumentException("Catalog charges cannot be negative");
        }
    }

    private record Commit<T>(
            T value,
            EconomyMutationResult creditMutation,
            EconomyMutationResult pointsMutation) {
    }
}
