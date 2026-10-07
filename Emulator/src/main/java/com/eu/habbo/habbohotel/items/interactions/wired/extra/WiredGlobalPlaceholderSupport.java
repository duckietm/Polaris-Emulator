package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The typed global placeholders an owner's other rooms share with a room. Read from the database when
 * an editor opens or saves; at firing time only loaded rooms are looked at.
 */
final class WiredGlobalPlaceholderSupport {
    static final String INTERACTION_TYPE = "wf_xtra_text_output_global";
    static final int MAX_SHARED_PLACEHOLDERS = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredGlobalPlaceholderSupport.class);
    private static final int MAX_SCANNED_ROWS = 500;
    private static final String SHARED_SQL = "SELECT rooms.id AS room_id, rooms.name AS room_name, items.wired_data "
            + "FROM rooms "
            + "INNER JOIN items ON rooms.id = items.room_id "
            + "INNER JOIN items_base ON items.item_id = items_base.id "
            + "WHERE rooms.owner_id = ? AND rooms.id <> ? "
            + "AND (items_base.interaction_type = ? OR items_base.item_name = ?) "
            + "ORDER BY rooms.name ASC, items.id ASC LIMIT ?";

    private WiredGlobalPlaceholderSupport() {}

    record SharedPlaceholder(int roomId, String roomName, String name, String value) {}

    static List<SharedPlaceholder> loadShared(Room room) {
        if (room == null || room.getOwnerId() <= 0) {
            return Collections.emptyList();
        }

        Database database = WiredPlatform.database();
        DataSource dataSource = (database != null) ? database.getDataSource() : null;
        if (dataSource == null) {
            return Collections.emptyList();
        }

        return loadShared(dataSource, room.getOwnerId(), room.getId());
    }

    /** By interaction or by item name, like the item loader's fallback for {@code wf_} items. */
    static List<SharedPlaceholder> loadShared(DataSource dataSource, int ownerId, int roomId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SHARED_SQL)) {
            statement.setInt(1, ownerId);
            statement.setInt(2, roomId);
            statement.setString(3, INTERACTION_TYPE);
            statement.setString(4, INTERACTION_TYPE);
            statement.setInt(5, MAX_SCANNED_ROWS);

            try (ResultSet set = statement.executeQuery()) {
                return readShared(set);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load shared global placeholders for room {}", roomId, e);
            return Collections.emptyList();
        }
    }

    static List<SharedPlaceholder> readShared(ResultSet set) throws SQLException {
        List<SharedPlaceholder> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        while (set.next() && result.size() < MAX_SHARED_PLACEHOLDERS) {
            WiredExtraTextOutputGlobal.JsonData data =
                    WiredExtraTextOutputGlobal.parseJsonData(set.getString("wired_data"));
            if (data == null
                    || WiredExtraTextOutputGlobal.normalizeMode(data.mode)
                            != WiredExtraTextOutputGlobal.MODE_FROM_VALUE) {
                continue;
            }

            String name = WiredExtraTextOutputGlobal.normalizePlaceholderName(data.placeholderName);
            int roomId = set.getInt("room_id");
            if (name.isEmpty() || !seen.add(roomId + "\t" + name)) {
                continue;
            }

            result.add(new SharedPlaceholder(
                    roomId,
                    sanitizeLabel(set.getString("room_name")),
                    name,
                    WiredExtraTextOutputGlobal.normalizeValue(data.value)));
        }

        return result;
    }

    static SharedPlaceholder findShared(Room room, int sourceRoomId, String name) {
        if (sourceRoomId <= 0 || name == null || name.isEmpty()) {
            return null;
        }

        for (SharedPlaceholder shared : loadShared(room)) {
            if (shared.roomId() == sourceRoomId && shared.name().equals(name)) {
                return shared;
            }
        }

        return null;
    }

    /** The current text of a typed global placeholder in a loaded room of the same owner, or null. */
    static String liveValue(Room room, int sourceRoomId, String name) {
        if (room == null || sourceRoomId <= 0 || name == null || name.isEmpty()) {
            return null;
        }

        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (environment == null || environment.getRoomManager() == null) {
            return null;
        }

        return typedValueIn(environment.getRoomManager().getRoom(sourceRoomId), room.getOwnerId(), name);
    }

    static String typedValueIn(Room source, int ownerId, String name) {
        if (source == null || source.getOwnerId() != ownerId || source.getRoomSpecialTypes() == null) {
            return null;
        }

        for (InteractionWiredExtra extra : source.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraTextOutputGlobal global
                    && global.getMode() == WiredExtraTextOutputGlobal.MODE_FROM_VALUE
                    && name.equals(global.getPlaceholderName())) {
                return global.getValue();
            }
        }

        return null;
    }

    private static String sanitizeLabel(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().replace("\t", "").replace("\r", "").replace("\n", "");
    }
}
