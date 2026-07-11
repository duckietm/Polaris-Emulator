package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

final class RedeemItemClaim {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedeemItemClaim.class);
    private static final String DELETE_OWNED_ITEM = "DELETE FROM items WHERE id = ? AND user_id = ? LIMIT 1";

    private RedeemItemClaim() {
    }

    static boolean tryClaim(int itemId, int userId) {
        if (itemId <= 0 || userId <= 0) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_OWNED_ITEM)) {
            statement.setInt(1, itemId);
            statement.setInt(2, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            LOGGER.error("Failed to claim redeemable item {} for user {}", itemId, userId, e);
            return false;
        }
    }
}
