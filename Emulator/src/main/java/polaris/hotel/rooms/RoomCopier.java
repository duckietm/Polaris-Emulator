package polaris.hotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import polaris.hotel.items.WebApiBoxSql;

/**
 * Copies a room as it is stored: its settings, its furni with their state and wired data, its custom floor plan,
 * its bots, its word filter and its wired settings and variables, into a new room of another owner.
 *
 * <p>Left behind on purpose: what belongs to the original and its visitors (score, rights, bans, mutes, the group,
 * the promotion, pets, the public / staff-picked marks), what holds value of its own (a chest's contents, a limited
 * number, a teleport's link) and the Web API box, whose settings are the owner's.
 *
 * <p>Wired boxes of the copy point at the copied furni ({@link RoomCopyWiredData}). Everything is written on the
 * caller's connection, which the caller commits or rolls back. The custom floor plan table is not transactional,
 * so the floor plan is written last: a copy that fails before it leaves nothing behind.
 */
public final class RoomCopier {
    /** Room columns that are not copied: the copy's own identity, and what the original earned. */
    static final Set<String> ROOM_COLUMNS_NOT_COPIED = Set.of(
            "id",
            "owner_id",
            "owner_name",
            "users",
            "guild_id",
            "score",
            "is_public",
            "is_staff_picked",
            "promoted",
            "is_forsale",
            "poll_id",
            "date_created");

    /** Bot columns that are not copied: the copy's own identity. */
    static final Set<String> BOT_COLUMNS_NOT_COPIED = Set.of("id", "user_id", "room_id");

    public record Result(int roomId, int items, int bots) {}

    private RoomCopier() {}

    /** The columns of a table that a copy takes over as they are, in the table's order. */
    static List<String> copiedColumns(List<String> columns, Set<String> notCopied) {
        List<String> copied = new ArrayList<>();
        for (String column : columns) {
            // Names come from the schema, but they end up in a statement: anything unusual is left out.
            if (column == null || !column.matches("[A-Za-z0-9_]+")) continue;
            if (!notCopied.contains(column.toLowerCase(Locale.ROOT))) copied.add(column);
        }
        return copied;
    }

    static String quoted(List<String> columns) {
        StringBuilder builder = new StringBuilder();
        for (String column : columns) {
            if (builder.length() > 0) builder.append(", ");
            builder.append('`').append(column).append('`');
        }
        return builder.toString();
    }

    /**
     * Writes the copy of room {@code sourceRoomId} for the given owner.
     *
     * @return the new room, or null when the source room has no row
     */
    public static Result copy(Connection connection, int sourceRoomId, int ownerId, String ownerName)
            throws SQLException {
        int roomId = copyRoomRow(connection, sourceRoomId, ownerId, ownerName);
        if (roomId == 0) return null;

        Map<Integer, Integer> itemIds = copyItems(connection, sourceRoomId, roomId, ownerId);
        repointWired(connection, roomId, itemIds);
        copyWiredState(connection, sourceRoomId, roomId, itemIds);
        copyWordFilter(connection, sourceRoomId, roomId);
        int bots = copyBots(connection, sourceRoomId, roomId, ownerId);
        copyFloorPlan(connection, sourceRoomId, roomId);

        return new Result(roomId, itemIds.size(), bots);
    }

    private static List<String> columnsOf(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE()"
                        + " AND table_name = ? ORDER BY ordinal_position")) {
            statement.setString(1, table);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) columns.add(set.getString(1));
            }
        }
        return columns;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        return !columnsOf(connection, table).isEmpty();
    }

    private static int copyRoomRow(Connection connection, int sourceRoomId, int ownerId, String ownerName)
            throws SQLException {
        String columns = quoted(copiedColumns(columnsOf(connection, "rooms"), ROOM_COLUMNS_NOT_COPIED));
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO rooms (owner_id, owner_name, " + columns + ") SELECT ?, ?, " + columns
                        + " FROM rooms WHERE id = ?",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, ownerId);
            statement.setString(2, ownerName);
            statement.setInt(3, sourceRoomId);
            statement.execute();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private static void copyFloorPlan(Connection connection, int sourceRoomId, int roomId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO room_models_custom (id, name, door_x, door_y, door_dir, heightmap)"
                        + " SELECT ?, ?, door_x, door_y, door_dir, heightmap FROM room_models_custom WHERE id = ?"
                        + " LIMIT 1")) {
            statement.setInt(1, roomId);
            statement.setString(2, "custom_" + roomId);
            statement.setInt(3, sourceRoomId);
            statement.execute();
        }
    }

    /** Copies the furni one by one, so every copy is known by the id of its original. */
    private static Map<Integer, Integer> copyItems(Connection connection, int sourceRoomId, int roomId, int ownerId)
            throws SQLException {
        Map<Integer, Integer> newIdByOldId = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                        "SELECT id, item_id, wall_pos, x, y, z, rot, extra_data, wired_data, allow_underpass"
                                + " FROM items WHERE room_id = ? AND item_id NOT IN (" + WebApiBoxSql.BASE_ITEM_IDS
                                + ") ORDER BY id");
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO items (user_id, room_id, item_id, wall_pos, x, y, z, rot, extra_data, wired_data,"
                                + " limited_data, guild_id, allow_underpass) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                                + " '0:0', 0, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            select.setInt(1, sourceRoomId);
            try (ResultSet set = select.executeQuery()) {
                while (set.next()) {
                    insert.setInt(1, ownerId);
                    insert.setInt(2, roomId);
                    insert.setInt(3, set.getInt("item_id"));
                    insert.setString(4, set.getString("wall_pos"));
                    insert.setInt(5, set.getInt("x"));
                    insert.setInt(6, set.getInt("y"));
                    insert.setDouble(7, set.getDouble("z"));
                    insert.setInt(8, set.getInt("rot"));
                    insert.setString(9, set.getString("extra_data"));
                    insert.setString(10, set.getString("wired_data"));
                    insert.setObject(11, set.getObject("allow_underpass"));
                    insert.execute();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (keys.next()) newIdByOldId.put(set.getInt("id"), keys.getInt(1));
                    }
                }
            }
        }
        return newIdByOldId;
    }

    private static void repointWired(Connection connection, int roomId, Map<Integer, Integer> itemIds)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                        "SELECT id, wired_data FROM items WHERE room_id = ? AND wired_data IS NOT NULL"
                                + " AND wired_data <> ''");
                PreparedStatement update =
                        connection.prepareStatement("UPDATE items SET wired_data = ? WHERE id = ?")) {
            select.setInt(1, roomId);
            try (ResultSet set = select.executeQuery()) {
                while (set.next()) {
                    String data = set.getString("wired_data");
                    String repointed = RoomCopyWiredData.remap(data, itemIds);
                    if (repointed == null || repointed.equals(data)) continue;

                    update.setString(1, repointed);
                    update.setInt(2, set.getInt("id"));
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    /** The room's wired settings, and the values its room and furni variables hold, on the copied furni. */
    private static void copyWiredState(
            Connection connection, int sourceRoomId, int roomId, Map<Integer, Integer> itemIds) throws SQLException {
        if (tableExists(connection, "room_wired_settings")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT IGNORE INTO room_wired_settings (room_id, inspect_mask, modify_mask, timezone)"
                            + " SELECT ?, inspect_mask, modify_mask, timezone FROM room_wired_settings"
                            + " WHERE room_id = ?")) {
                statement.setInt(1, roomId);
                statement.setInt(2, sourceRoomId);
                statement.execute();
            }
        }

        if (tableExists(connection, "room_wired_variables")) {
            try (PreparedStatement select = connection.prepareStatement(
                            "SELECT variable_item_id, value FROM room_wired_variables WHERE room_id = ?");
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT IGNORE INTO room_wired_variables (room_id, variable_item_id, value, created_at,"
                                    + " updated_at) VALUES (?, ?, ?, UNIX_TIMESTAMP(), UNIX_TIMESTAMP())")) {
                select.setInt(1, sourceRoomId);
                try (ResultSet set = select.executeQuery()) {
                    while (set.next()) {
                        Integer variable = itemIds.get(set.getInt("variable_item_id"));
                        if (variable == null) continue;

                        insert.setInt(1, roomId);
                        insert.setInt(2, variable);
                        insert.setObject(3, set.getObject("value"));
                        insert.addBatch();
                    }
                }
                insert.executeBatch();
            }
        }

        if (tableExists(connection, "room_furni_wired_variables")) {
            try (PreparedStatement select = connection.prepareStatement(
                            "SELECT furni_id, variable_item_id, value FROM room_furni_wired_variables WHERE room_id = ?");
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT IGNORE INTO room_furni_wired_variables (room_id, furni_id, variable_item_id, value,"
                                    + " created_at, updated_at) VALUES (?, ?, ?, ?, UNIX_TIMESTAMP(),"
                                    + " UNIX_TIMESTAMP())")) {
                select.setInt(1, sourceRoomId);
                try (ResultSet set = select.executeQuery()) {
                    while (set.next()) {
                        Integer furni = itemIds.get(set.getInt("furni_id"));
                        Integer variable = itemIds.get(set.getInt("variable_item_id"));
                        if (furni == null || variable == null) continue;

                        insert.setInt(1, roomId);
                        insert.setInt(2, furni);
                        insert.setInt(3, variable);
                        insert.setObject(4, set.getObject("value"));
                        insert.addBatch();
                    }
                }
                insert.executeBatch();
            }
        }
    }

    private static void copyWordFilter(Connection connection, int sourceRoomId, int roomId) throws SQLException {
        if (!tableExists(connection, "room_wordfilter")) return;

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO room_wordfilter (room_id, word) SELECT ?, word FROM room_wordfilter"
                        + " WHERE room_id = ?")) {
            statement.setInt(1, roomId);
            statement.setInt(2, sourceRoomId);
            statement.execute();
        }
    }

    private static int copyBots(Connection connection, int sourceRoomId, int roomId, int ownerId) throws SQLException {
        String columns = quoted(copiedColumns(columnsOf(connection, "bots"), BOT_COLUMNS_NOT_COPIED));
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO bots (user_id, room_id, " + columns
                + ") SELECT ?, ?, " + columns + " FROM bots WHERE room_id = ?")) {
            statement.setInt(1, ownerId);
            statement.setInt(2, roomId);
            statement.setInt(3, sourceRoomId);
            return statement.executeUpdate();
        }
    }
}
