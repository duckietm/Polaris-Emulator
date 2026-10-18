package com.eu.habbo.habbohotel.wired.link;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The WIRED Link tables in the hotel database (V20261017220000). */
final class JdbcWiredLinkRepository implements WiredLinkRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcWiredLinkRepository.class);

    private static final String NETWORKS_SQL =
            "SELECT id, owner_id, name FROM wired_link_networks WHERE owner_id = ? ORDER BY id";
    private static final String ENDPOINTS_SQL =
            "SELECT item_id, network_id, room_id, owner_id, receive_local FROM wired_link_endpoints WHERE owner_id = ?";
    private static final String CREATE_SQL =
            "INSERT INTO wired_link_networks (owner_id, name, created_at) VALUES (?, ?, UNIX_TIMESTAMP())";
    private static final String RENAME_SQL = "UPDATE wired_link_networks SET name = ? WHERE id = ?";
    private static final String DISCONNECT_SQL =
            "UPDATE wired_link_endpoints SET network_id = NULL WHERE network_id = ?";
    private static final String DELETE_SQL = "DELETE FROM wired_link_networks WHERE id = ?";
    private static final String SAVE_ENDPOINT_SQL =
            "INSERT INTO wired_link_endpoints (item_id, network_id, room_id, owner_id, receive_local) VALUES (?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE network_id = VALUES(network_id), room_id = VALUES(room_id),"
                    + " owner_id = VALUES(owner_id), receive_local = VALUES(receive_local)";
    private static final String DELETE_ENDPOINT_SQL = "DELETE FROM wired_link_endpoints WHERE item_id = ?";
    private static final String ROOM_NAME_SQL = "SELECT name FROM rooms WHERE id = ?";

    private Connection connection() throws SQLException {
        Database database = WiredPlatform.database();
        if (database == null) {
            throw new SQLException("No database");
        }
        return database.getDataSource().getConnection();
    }

    @Override
    public List<WiredLinkNetwork> networksOf(int ownerId) {
        try (Connection connection = this.connection();
                PreparedStatement statement = connection.prepareStatement(NETWORKS_SQL)) {
            statement.setInt(1, ownerId);
            List<WiredLinkNetwork> networks = new ArrayList<>();
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    networks.add(new WiredLinkNetwork(set.getInt("id"), set.getInt("owner_id"), set.getString("name")));
                }
            }
            return networks;
        } catch (SQLException e) {
            LOGGER.error("Failed to read the WIRED Link networks of user {}", ownerId, e);
            return null;
        }
    }

    @Override
    public List<WiredLinkEndpoint> endpointsOf(int ownerId) {
        try (Connection connection = this.connection();
                PreparedStatement statement = connection.prepareStatement(ENDPOINTS_SQL)) {
            statement.setInt(1, ownerId);
            List<WiredLinkEndpoint> endpoints = new ArrayList<>();
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    endpoints.add(new WiredLinkEndpoint(
                            set.getInt("item_id"),
                            set.getInt("network_id"),
                            set.getInt("room_id"),
                            set.getInt("owner_id"),
                            set.getBoolean("receive_local")));
                }
            }
            return endpoints;
        } catch (SQLException e) {
            LOGGER.error("Failed to read the WIRED Link endpoints of user {}", ownerId, e);
            return null;
        }
    }

    @Override
    public WiredLinkNetwork createNetwork(int ownerId, String name) {
        try (Connection connection = this.connection();
                PreparedStatement statement =
                        connection.prepareStatement(CREATE_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, ownerId);
            statement.setString(2, name);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? new WiredLinkNetwork(keys.getInt(1), ownerId, name) : null;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to create a WIRED Link network for user {}", ownerId, e);
            return null;
        }
    }

    @Override
    public boolean renameNetwork(int networkId, String name) {
        try (Connection connection = this.connection();
                PreparedStatement statement = connection.prepareStatement(RENAME_SQL)) {
            statement.setString(1, name);
            statement.setInt(2, networkId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to rename WIRED Link network {}", networkId, e);
            return false;
        }
    }

    @Override
    public boolean deleteNetwork(int networkId) {
        try (Connection connection = this.connection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement disconnect = connection.prepareStatement(DISCONNECT_SQL);
                    PreparedStatement delete = connection.prepareStatement(DELETE_SQL)) {
                disconnect.setInt(1, networkId);
                disconnect.executeUpdate();
                delete.setInt(1, networkId);
                int deleted = delete.executeUpdate();
                connection.commit();
                return deleted > 0;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to delete WIRED Link network {}", networkId, e);
            return false;
        }
    }

    @Override
    public boolean saveEndpoint(WiredLinkEndpoint endpoint) {
        try (Connection connection = this.connection();
                PreparedStatement statement = connection.prepareStatement(SAVE_ENDPOINT_SQL)) {
            statement.setInt(1, endpoint.itemId());
            if (endpoint.connected()) {
                statement.setInt(2, endpoint.networkId());
            } else {
                statement.setNull(2, java.sql.Types.INTEGER);
            }
            statement.setInt(3, endpoint.roomId());
            statement.setInt(4, endpoint.ownerId());
            statement.setBoolean(5, endpoint.receiveLocal());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to save WIRED Link {}", endpoint.itemId(), e);
            return false;
        }
    }

    @Override
    public boolean deleteEndpoint(int itemId) {
        try (Connection connection = this.connection();
                PreparedStatement statement = connection.prepareStatement(DELETE_ENDPOINT_SQL)) {
            statement.setInt(1, itemId);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to delete WIRED Link {}", itemId, e);
            return false;
        }
    }

    @Override
    public Map<Integer, String> roomNames(Collection<Integer> roomIds) {
        Map<Integer, String> names = new HashMap<>();
        if (roomIds == null || roomIds.isEmpty()) {
            return names;
        }
        try (Connection connection = this.connection();
                PreparedStatement statement = connection.prepareStatement(ROOM_NAME_SQL)) {
            for (Integer roomId : roomIds) {
                if (roomId == null) continue;
                statement.setInt(1, roomId);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        names.put(roomId, set.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to read WIRED Link room names", e);
        }
        return names;
    }
}
