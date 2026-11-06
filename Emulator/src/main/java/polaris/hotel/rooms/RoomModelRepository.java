package polaris.hotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RoomModelRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomModelRepository.class);
    private final RoomDependencies.ConnectionProvider database;
    private final List<String> modelNames = new ArrayList<>();
    private final ConcurrentHashMap<String, RoomManager.RoomLayoutData> layouts = new ConcurrentHashMap<>();

    RoomModelRepository(RoomDependencies.ConnectionProvider database) {
        this.database = database;
    }

    List<String> modelNames() {
        return this.modelNames;
    }

    ConcurrentHashMap<String, RoomManager.RoomLayoutData> layouts() {
        return this.layouts;
    }

    void reload() {
        this.modelNames.clear();
        this.layouts.clear();
        try (Connection connection = this.database.openConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM room_models")) {
            while (set.next()) {
                String name = set.getString("name");
                this.modelNames.add(name);
                this.layouts.put(name, new RoomManager.RoomLayoutData(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    CustomRoomLayout loadCustomLayout(Room room) {
        CustomRoomLayout layout = null;
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM room_models_custom WHERE id = ? LIMIT 1")) {
            statement.setInt(1, room.getId());
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    layout = new CustomRoomLayout(set, room);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return layout;
    }

    boolean exists(String name) {
        return this.modelNames.contains(name);
    }

    RoomLayout load(String name, Room room) {
        RoomManager.RoomLayoutData cached = this.layouts.get(name);
        if (cached != null) {
            return new RoomLayout(cached, room);
        }

        RoomLayout layout = null;
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM room_models WHERE name = ? LIMIT 1")) {
            statement.setString(1, name);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    layout = new RoomLayout(set, room);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return layout;
    }
}
