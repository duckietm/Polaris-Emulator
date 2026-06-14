package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

final class HousekeepingMutationGuard {
    static final int MAX_GRANT = 1_000_000_000;

    private HousekeepingMutationGuard() {
    }

    static boolean isPositiveGrantAmount(int amount) {
        return amount > 0 && amount <= MAX_GRANT;
    }

    static boolean isCurrencyType(int currencyType) {
        return currencyType >= 0;
    }

    static boolean userExists(int userId) {
        if (userId <= 0) {
            return false;
        }

        if (Emulator.getGameEnvironment().getHabboManager().getHabbo(userId) != null) {
            return true;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    static boolean itemExists(int itemId) {
        return itemId > 0 && Emulator.getGameEnvironment().getItemManager().getItem(itemId) != null;
    }
}
