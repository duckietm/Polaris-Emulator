package com.eu.habbo.habbohotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** The rows of {@code room_user_wired_variables}; not final so tests can keep them in memory. */
class RoomUserVariableRepository {
    private static final String FIND_BY_USER_SQL = """
            SELECT variable_item_id, value, created_at, updated_at
            FROM room_user_wired_variables
            WHERE room_id = ? AND user_id = ?
            """;
    private static final String FIND_UNIT_HOLDERS_SQL = """
            SELECT user_id, variable_item_id, value, created_at, updated_at
            FROM room_user_wired_variables
            WHERE room_id = ? AND user_id < 0
            """;
    private static final String UPSERT_SQL =
            "INSERT INTO room_user_wired_variables (room_id, user_id, variable_item_id, value, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value), "
                    + "updated_at = VALUES(updated_at)";

    private static final String FIND_ONE_SQL = """
            SELECT value, created_at, updated_at
            FROM room_user_wired_variables
            WHERE room_id = ? AND user_id = ? AND variable_item_id = ?
            """;
    private static final String HAS_USER_SQL =
            "SELECT 1 FROM room_user_wired_variables WHERE room_id = ? AND user_id = ? LIMIT 1";
    private static final String UPDATE_VALUE_SQL = "UPDATE room_user_wired_variables SET value = ?, updated_at = ? "
            + "WHERE room_id = ? AND user_id = ? AND variable_item_id = ?";
    private static final String USERNAME_SQL = "SELECT username FROM users WHERE id = ? LIMIT 1";
    private static final String PARTICIPANT_BY_NAME_SQL = "SELECT users.id FROM users WHERE users.username = ? "
            + "AND EXISTS (SELECT 1 FROM room_user_wired_variables saved "
            + "WHERE saved.room_id = ? AND saved.user_id = users.id) LIMIT 1";

    private final Supplier<? extends DataSource> dataSource;

    RoomUserVariableRepository(DataSource dataSource) {
        this(() -> dataSource);
    }

    RoomUserVariableRepository(Supplier<? extends DataSource> dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    List<StoredAssignment> findByUser(int roomId, int userId) throws SQLException {
        List<StoredAssignment> assignments = new ArrayList<>();
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_BY_USER_SQL)) {
            statement.setInt(1, roomId);
            statement.setInt(2, userId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int rawValue = set.getInt("value");
                    Integer value = set.wasNull() ? null : rawValue;
                    assignments.add(new StoredAssignment(
                            set.getInt("variable_item_id"), value, set.getInt("created_at"), set.getInt("updated_at")));
                }
            }
        }
        return assignments;
    }

    /** Every stored row of the room's pets and bots, by holder key, in one query. */
    Map<Integer, List<StoredAssignment>> findUnitHolders(int roomId) throws SQLException {
        Map<Integer, List<StoredAssignment>> holders = new HashMap<>();
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_UNIT_HOLDERS_SQL)) {
            statement.setInt(1, roomId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int rawValue = set.getInt("value");
                    Integer value = set.wasNull() ? null : rawValue;
                    holders.computeIfAbsent(set.getInt("user_id"), key -> new ArrayList<>())
                            .add(new StoredAssignment(
                                    set.getInt("variable_item_id"),
                                    value,
                                    set.getInt("created_at"),
                                    set.getInt("updated_at")));
                }
            }
        }
        return holders;
    }

    void upsert(int roomId, int userId, int definitionItemId, Integer value, int createdAt, int updatedAt)
            throws SQLException {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setInt(1, roomId);
            statement.setInt(2, userId);
            statement.setInt(3, definitionItemId);
            if (value == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, value);
            }
            statement.setInt(5, createdAt);
            statement.setInt(6, updatedAt);
            statement.executeUpdate();
        }
    }

    /** Answers whether a row was there. */
    boolean delete(int roomId, int userId, int definitionItemId) throws SQLException {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM room_user_wired_variables "
                        + "WHERE room_id = ? AND user_id = ? AND variable_item_id = ?")) {
            statement.setInt(1, roomId);
            statement.setInt(2, userId);
            statement.setInt(3, definitionItemId);
            return statement.executeUpdate() > 0;
        }
    }

    /** One user's row of one variable, or null. */
    StoredAssignment find(int roomId, int userId, int definitionItemId) throws SQLException {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_ONE_SQL)) {
            statement.setInt(1, roomId);
            statement.setInt(2, userId);
            statement.setInt(3, definitionItemId);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return null;
                }
                int rawValue = set.getInt("value");
                Integer value = set.wasNull() ? null : rawValue;
                return new StoredAssignment(
                        definitionItemId, value, set.getInt("created_at"), set.getInt("updated_at"));
            }
        }
    }

    /** Whether the user has any saved row in the room. */
    boolean hasUser(int roomId, int userId) throws SQLException {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(HAS_USER_SQL)) {
            statement.setInt(1, roomId);
            statement.setInt(2, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    /** Sets the value of a row that exists; answers whether it did. */
    boolean updateValue(int roomId, int userId, int definitionItemId, Integer value, int updatedAt)
            throws SQLException {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_VALUE_SQL)) {
            if (value == null) {
                statement.setNull(1, Types.INTEGER);
            } else {
                statement.setInt(1, value);
            }
            statement.setInt(2, updatedAt);
            statement.setInt(3, roomId);
            statement.setInt(4, userId);
            statement.setInt(5, definitionItemId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * One page of the saved rows of users (not pets or bots) for a variable, leaving out the given
     * users, in the web API's order (the user id breaks ties). {@code dayStart} is set for a daily
     * counter, whose older values read as 0.
     */
    List<SavedUser> pageUsers(
            int roomId,
            int definitionItemId,
            RoomUserVariableStore.Order order,
            boolean descending,
            Integer dayStart,
            Collection<Integer> excluded,
            int offset,
            int limit)
            throws SQLException {
        List<SavedUser> rows = new ArrayList<>();
        boolean dailyValue = order == RoomUserVariableStore.Order.VALUE && dayStart != null;
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(pageUsersSql(order, descending, dailyValue, excluded.size()))) {
            int index = bindUsers(statement, roomId, definitionItemId, excluded);
            if (dailyValue) {
                statement.setInt(index++, dayStart);
            }
            statement.setInt(index++, limit);
            statement.setInt(index, offset);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int rawValue = set.getInt("value");
                    Integer value = set.wasNull() ? null : rawValue;
                    rows.add(new SavedUser(
                            set.getInt("user_id"),
                            set.getString("username"),
                            value,
                            set.getInt("created_at"),
                            set.getInt("updated_at")));
                }
            }
        }
        return rows;
    }

    /** How many users (not pets or bots) have a saved row of the variable, leaving out the given users. */
    int countUsers(int roomId, int definitionItemId, Collection<Integer> excluded) throws SQLException {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM room_user_wired_variables saved " + usersWhere(excluded.size()))) {
            bindUsers(statement, roomId, definitionItemId, excluded);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        }
    }

    String username(int userId) throws SQLException {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(USERNAME_SQL)) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getString("username") : null;
            }
        }
    }

    /** The id of the user with this name if they have a saved row in the room, else 0. */
    int participantIdByName(int roomId, String username) throws SQLException {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(PARTICIPANT_BY_NAME_SQL)) {
            statement.setString(1, username);
            statement.setInt(2, roomId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        }
    }

    static String pageUsersSql(
            RoomUserVariableStore.Order order, boolean descending, boolean dailyValue, int excluded) {
        String direction = descending ? " DESC" : " ASC";
        String key =
                switch (order) {
                    case ID -> null;
                    case VALUE ->
                        dailyValue
                                ? "CASE WHEN saved.value IS NULL OR saved.value = 0 OR saved.updated_at >= ? "
                                        + "THEN saved.value ELSE 0 END"
                                : "saved.value";
                    case CREATION_TIME -> "saved.created_at";
                    case UPDATE_TIME -> "saved.updated_at";
                };
        return "SELECT saved.user_id, saved.value, saved.created_at, saved.updated_at, users.username "
                + "FROM room_user_wired_variables saved LEFT JOIN users ON users.id = saved.user_id "
                + usersWhere(excluded)
                + " ORDER BY " + (key == null ? "" : key + direction + ", ") + "saved.user_id" + direction
                + " LIMIT ? OFFSET ?";
    }

    private static String usersWhere(int excluded) {
        StringBuilder where =
                new StringBuilder("WHERE saved.room_id = ? AND saved.variable_item_id = ? AND saved.user_id > 0");
        if (excluded > 0) {
            where.append(" AND saved.user_id NOT IN (");
            for (int index = 0; index < excluded; index++) {
                where.append(index == 0 ? "?" : ", ?");
            }
            where.append(')');
        }
        return where.toString();
    }

    private static int bindUsers(
            PreparedStatement statement, int roomId, int definitionItemId, Collection<Integer> excluded)
            throws SQLException {
        int index = 1;
        statement.setInt(index++, roomId);
        statement.setInt(index++, definitionItemId);
        for (Integer userId : excluded) {
            statement.setInt(index++, userId);
        }
        return index;
    }

    void deleteDefinition(int roomId, int definitionItemId) throws SQLException {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM room_user_wired_variables WHERE room_id = ? AND variable_item_id = ?")) {
            statement.setInt(1, roomId);
            statement.setInt(2, definitionItemId);
            statement.executeUpdate();
        }
    }

    boolean hasDefinition(int roomId, int definitionItemId) throws SQLException {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM room_user_wired_variables WHERE room_id = ? AND variable_item_id = ? LIMIT 1")) {
            statement.setInt(1, roomId);
            statement.setInt(2, definitionItemId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    record StoredAssignment(int definitionItemId, Integer value, int createdAt, int updatedAt) {}

    record SavedUser(int userId, String username, Integer value, int createdAt, int updatedAt) {}
}
