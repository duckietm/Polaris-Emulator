package polaris.hotel.users;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;

public class UserNickIcon implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserNickIcon.class);

    private int id;
    private final int userId;
    private String iconKey;
    private boolean active;
    private boolean needsInsert;
    private boolean needsUpdate;
    private boolean needsDelete;

    public UserNickIcon(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.userId = set.getInt("user_id");
        this.iconKey = set.getString("icon_key");
        this.active = set.getBoolean("active");
        this.needsInsert = false;
        this.needsUpdate = false;
        this.needsDelete = false;
    }

    public UserNickIcon(int userId, String iconKey) {
        this.id = 0;
        this.userId = userId;
        this.iconKey = iconKey;
        this.active = false;
        this.needsInsert = true;
        this.needsUpdate = false;
        this.needsDelete = false;
    }

    @Override
    public void run() {
        try {
            if (this.needsInsert) {
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO user_nick_icons (user_id, icon_key, active) VALUES (?, ?, ?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, this.userId);
                    statement.setString(2, this.iconKey);
                    statement.setBoolean(3, this.active);
                    statement.execute();

                    try (ResultSet set = statement.getGeneratedKeys()) {
                        if (set.next()) {
                            this.id = set.getInt(1);
                        }
                    }
                }

                this.needsInsert = false;
            } else if (this.needsDelete) {
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "DELETE FROM user_nick_icons WHERE id = ? AND user_id = ?")) {
                    statement.setInt(1, this.id);
                    statement.setInt(2, this.userId);
                    statement.execute();
                }

                this.needsDelete = false;
            } else if (this.needsUpdate) {
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "UPDATE user_nick_icons SET icon_key = ?, active = ? WHERE id = ? AND user_id = ?")) {
                    statement.setString(1, this.iconKey);
                    statement.setBoolean(2, this.active);
                    statement.setInt(3, this.id);
                    statement.setInt(4, this.userId);
                    statement.execute();
                }

                this.needsUpdate = false;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public int getId() {
        return this.id;
    }

    public int getUserId() {
        return this.userId;
    }

    public String getIconKey() {
        return this.iconKey;
    }

    public void setIconKey(String iconKey) {
        this.iconKey = iconKey;
        this.needsUpdate = true;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.needsUpdate = true;
    }

    public void needsDelete(boolean needsDelete) {
        this.needsDelete = needsDelete;
    }
}
