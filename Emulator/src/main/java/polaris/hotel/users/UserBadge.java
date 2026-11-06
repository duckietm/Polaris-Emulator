package polaris.hotel.users;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;

public class UserBadge implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserBadge.class);
    private int id;
    private String code;
    private int slot;
    private User user;
    private boolean needsUpdate;
    private boolean needsInsert;

    public UserBadge(ResultSet set, User user) throws SQLException {
        this.id = set.getInt("id");
        this.code = set.getString("badge_code");
        this.slot = set.getInt("slot_id");
        this.user = user;
        this.needsUpdate = false;
        this.needsInsert = false;
    }

    public UserBadge(int id, String code, int slot, User user) {
        this.id = id;
        this.code = code;
        this.slot = slot;
        this.user = user;
        this.needsUpdate = false;
        this.needsInsert = true;
    }

    public int getId() {
        return this.id;
    }

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getSlot() {
        return this.slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    @Override
    public void run() {
        try {
            if (this.needsInsert) {
                try (Connection connection =
                        Emulator.getDatabase().getDataSource().getConnection()) {
                    this.insert(connection);
                }
            } else if (this.needsUpdate) {
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "UPDATE users_badges SET slot_id = ?, badge_code = ? WHERE id = ? AND user_id = ?")) {
                    statement.setInt(1, this.slot);
                    statement.setString(2, this.code);
                    statement.setInt(3, this.id);
                    statement.setInt(4, this.user.getUserInfo().getId());
                    statement.execute();
                }
                this.needsUpdate = false;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void insert(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users_badges (user_id, slot_id, badge_code) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, this.user.getUserInfo().getId());
            statement.setInt(2, this.slot);
            statement.setString(3, this.code);
            statement.executeUpdate();
            try (ResultSet set = statement.getGeneratedKeys()) {
                if (!set.next()) throw new SQLException("Unable to create user badge");
                this.id = set.getInt(1);
            }
        }
        this.needsInsert = false;
    }

    public void needsUpdate(boolean needsUpdate) {
        this.needsUpdate = needsUpdate;
    }

    public void needsInsert(boolean needsInsert) {
        this.needsInsert = needsInsert;
    }
}
