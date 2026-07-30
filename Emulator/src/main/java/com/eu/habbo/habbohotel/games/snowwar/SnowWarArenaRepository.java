package com.eu.habbo.habbohotel.games.snowwar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Database-backed catalogue for official and staff-built SnowWar arenas. */
public final class SnowWarArenaRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarArenaRepository.class);
    private static final List<SnowWarArenaDefinition> OFFICIAL_ARENAS = List.of(
            new SnowWarArenaDefinition(8, "Arctic Island", "snowstorm_arena_8", true),
            new SnowWarArenaDefinition(9, "Dragon Cave", "snowstorm_arena_9", true),
            new SnowWarArenaDefinition(11, "Fight Night", "snowstorm_arena_11", true));

    private final SnowWarConnectionProvider connections;

    SnowWarArenaRepository(SnowWarConnectionProvider connections) {
        this.connections = connections;
    }

    public List<SnowWarArenaDefinition> findActive() {
        List<SnowWarArenaDefinition> arenas = new ArrayList<>();
        try (Connection connection = this.connections.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id, name, model_name, is_official FROM snowwar_arenas "
                                + "WHERE is_active = 1 ORDER BY is_official DESC, id");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                arenas.add(read(result));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load the SnowWar arena catalogue; using official arenas.", e);
        }
        return arenas.isEmpty() ? OFFICIAL_ARENAS : List.copyOf(arenas);
    }

    public SnowWarArenaDefinition find(int arenaId) {
        try (Connection connection = this.connections.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, name, model_name, is_official FROM snowwar_arenas WHERE id = ?")) {
            statement.setInt(1, arenaId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return read(result);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load SnowWar arena {}.", arenaId, e);
        }
        return OFFICIAL_ARENAS.stream()
                .filter(arena -> arena.id() == arenaId)
                .findFirst()
                .orElse(null);
    }

    public SnowWarArenaDefinition createDraft(int userId) {
        String modelName = null;
        int arenaId = 0;
        try (Connection connection = this.connections.openConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO snowwar_arenas (name, created_by) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "Custom Arena");
                insert.setInt(2, userId);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) {
                        return null;
                    }
                    arenaId = keys.getInt(1);
                }
            }

            modelName = "snowstorm_custom_" + arenaId;
            try (PreparedStatement update =
                    connection.prepareStatement("UPDATE snowwar_arenas SET name = ?, model_name = ? WHERE id = ?")) {
                update.setString(1, "Custom Arena " + arenaId);
                update.setString(2, modelName);
                update.setInt(3, arenaId);
                update.executeUpdate();
            }

            try (PreparedStatement clone = connection.prepareStatement(
                    "INSERT INTO room_models (name, door_x, door_y, door_dir, heightmap, public_items, club_only) "
                            + "SELECT ?, door_x, door_y, door_dir, heightmap, public_items, club_only "
                            + "FROM room_models WHERE name = ?")) {
                clone.setString(1, modelName);
                clone.setString(2, "snowstorm_arena_1");
                if (clone.executeUpdate() != 1) {
                    discardDraft(connection, arenaId, userId);
                    return null;
                }
            }

            return new SnowWarArenaDefinition(arenaId, "Custom Arena " + arenaId, modelName, false);
        } catch (SQLException e) {
            LOGGER.error("Failed to create a SnowWar arena draft for user {}.", userId, e);
            if (arenaId > 0) {
                discardDraft(arenaId, userId);
            }
            return null;
        }
    }

    public boolean publish(int arenaId, int userId, String name) {
        try (Connection connection = this.connections.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE snowwar_arenas SET name = ?, is_active = 1 "
                                + "WHERE id = ? AND created_by = ? AND is_official = 0")) {
            statement.setString(1, name);
            statement.setInt(2, arenaId);
            statement.setInt(3, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            LOGGER.error("Failed to publish SnowWar arena {}.", arenaId, e);
            return false;
        }
    }

    public void discardDraft(int arenaId, int userId) {
        SnowWarArenaDefinition arena = this.find(arenaId);
        if (arena == null || arena.official()) {
            return;
        }
        try (Connection connection = this.connections.openConnection()) {
            discardDraft(connection, arenaId, userId);
        } catch (SQLException e) {
            LOGGER.error("Failed to discard SnowWar arena draft {}.", arenaId, e);
        }
    }

    private static void discardDraft(Connection connection, int arenaId, int userId) throws SQLException {
        try (PreparedStatement deleteModel = connection.prepareStatement(
                "DELETE room_models FROM room_models JOIN snowwar_arenas ON snowwar_arenas.model_name = room_models.name "
                        + "WHERE snowwar_arenas.id = ? AND snowwar_arenas.created_by = ? AND snowwar_arenas.is_active = 0")) {
            deleteModel.setInt(1, arenaId);
            deleteModel.setInt(2, userId);
            deleteModel.executeUpdate();
        }
        try (PreparedStatement deleteArena = connection.prepareStatement(
                "DELETE FROM snowwar_arenas WHERE id = ? AND created_by = ? AND is_active = 0")) {
            deleteArena.setInt(1, arenaId);
            deleteArena.setInt(2, userId);
            deleteArena.executeUpdate();
        }
    }

    private static SnowWarArenaDefinition read(ResultSet result) throws SQLException {
        return new SnowWarArenaDefinition(
                result.getInt("id"),
                result.getString("name"),
                result.getString("model_name"),
                result.getBoolean("is_official"));
    }
}
