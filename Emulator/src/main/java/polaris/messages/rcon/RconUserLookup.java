package polaris.messages.rcon;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import polaris.Emulator;

public class RconUserLookup {
    private RconUserLookup() {}

    public static boolean userExists(int userId) {
        if (Emulator.getGameEnvironment().getUserManager().getUser(userId) != null) {
            return true;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM users WHERE id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
