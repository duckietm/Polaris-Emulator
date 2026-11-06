package polaris.threading.runnables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;

class QueryDeleteUserBadge implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryDeleteUserBadge.class);

    private final String name;
    private final User user;

    public QueryDeleteUserBadge(User user, String name) {
        this.name = name;
        this.user = user;
    }

    @Override
    public void run() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM users_badges WHERE user_id = ? AND badge_code = ?")) {
            statement.setInt(1, this.user.getUserInfo().getId());
            statement.setString(2, this.name);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }
}
