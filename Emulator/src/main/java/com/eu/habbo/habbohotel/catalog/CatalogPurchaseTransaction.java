package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

final class CatalogPurchaseTransaction {
    private static final String CHARGE_CREDITS =
            "UPDATE users SET credits = credits - ? WHERE id = ? AND credits >= ?";
    private static final String CHARGE_POINTS =
            "UPDATE users_currency SET amount = amount - ? WHERE user_id = ? AND type = ? AND amount >= ?";

    private CatalogPurchaseTransaction() {
    }

    static <T> T execute(int userId, PurchaseWork<T> work) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                PreparedPurchase<T> purchase = work.persist(connection);
                chargeCredits(connection, userId, purchase.credits());
                chargePoints(connection, userId, purchase.pointsType(), purchase.points());
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

    private static void chargeCredits(Connection connection, int userId, int credits) throws SQLException {
        if (credits == 0) return;
        try (PreparedStatement statement = connection.prepareStatement(CHARGE_CREDITS)) {
            statement.setInt(1, credits);
            statement.setInt(2, userId);
            statement.setInt(3, credits);
            if (statement.executeUpdate() != 1) throw new SQLException("Insufficient credits for catalog purchase");
        }
    }

    private static void chargePoints(Connection connection, int userId, int pointsType, int points) throws SQLException {
        if (points == 0) return;
        try (PreparedStatement statement = connection.prepareStatement(CHARGE_POINTS)) {
            statement.setInt(1, points);
            statement.setInt(2, userId);
            statement.setInt(3, pointsType);
            statement.setInt(4, points);
            if (statement.executeUpdate() != 1) throw new SQLException("Insufficient points for catalog purchase");
        }
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
