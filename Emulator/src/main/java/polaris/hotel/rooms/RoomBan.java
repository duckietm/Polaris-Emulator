package polaris.hotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;

public class RoomBan {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomBan.class);

    public final int roomId;
    public final int userId;
    public final String username;
    public final int endTimestamp;

    public RoomBan(int roomId, int userId, String username, int endTimestamp) {
        this.roomId = roomId;
        this.userId = userId;
        this.username = username;
        this.endTimestamp = endTimestamp;
    }

    public RoomBan(ResultSet set) throws SQLException {
        this.roomId = set.getInt("room_id");
        this.userId = set.getInt("user_id");
        this.username = set.getString("username");
        this.endTimestamp = set.getInt("ends");
    }

    public void insert() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO room_bans (room_id, user_id, ends) VALUES (?, ?, ?)")) {
            statement.setInt(1, this.roomId);
            statement.setInt(2, this.userId);
            statement.setInt(3, this.endTimestamp);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void delete() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM room_bans WHERE room_id = ? AND user_id = ?")) {
            statement.setInt(1, this.roomId);
            statement.setInt(2, this.userId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }
}
