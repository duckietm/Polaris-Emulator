package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;

import java.sql.Connection;
import java.sql.SQLException;

final class CatalogPurchaseTransaction {
    private CatalogPurchaseTransaction() {
    }

    static <T> T execute(int userId, String operationId, PurchaseWork<T> work) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                PreparedPurchase<T> purchase = work.persist(connection);
                chargeCredits(connection, operationId, userId, purchase.credits());
                chargePoints(connection, operationId, userId, purchase.pointsType(), purchase.points());
                connection.commit();
                return purchase.value();
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

    private static void chargeCredits(Connection connection, String operationId, int userId, int credits)
            throws SQLException {
        if (credits == 0) return;
        EconomyLedger.apply(connection, new EconomyOperation(
                operationId + ":credits", userId, userId, "catalog_purchase", "catalog.purchase",
                EconomyLedger.CREDITS, -credits, null, operationId));
    }

    private static void chargePoints(Connection connection, String operationId, int userId, int pointsType, int points)
            throws SQLException {
        if (points == 0) return;
        EconomyLedger.apply(connection, new EconomyOperation(
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
}
