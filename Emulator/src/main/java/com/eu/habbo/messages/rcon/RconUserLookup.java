package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class RconUserLookup {
    private RconUserLookup() {
    }

    public static boolean userExists(int userId) {
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
}
