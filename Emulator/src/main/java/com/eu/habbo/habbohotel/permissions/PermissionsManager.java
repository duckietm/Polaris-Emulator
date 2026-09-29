package com.eu.habbo.habbohotel.permissions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.HabboPlugin;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionsManager.class);

    // A reload builds new maps and swaps them in, so checks on other threads never see one half-filled.
    private volatile Int2ObjectMap<Rank> ranks;
    private volatile Int2IntMap enables;
    private volatile Map<String, List<Rank>> badges;
    private volatile boolean normalizedSchemaEnabled;

    public PermissionsManager() {
        long millis = System.currentTimeMillis();
        this.ranks = new Int2ObjectOpenHashMap<>();
        this.enables = new Int2IntOpenHashMap();
        this.badges = new HashMap<>();

        this.reload();

        LOGGER.info("Permissions Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public void reload() {
        if (Emulator.getDatabase() != null && Emulator.getDatabase().getLegacySqlBridge() != null) {
            Emulator.getDatabase().getLegacySqlBridge().invalidateCaches();
        }

        this.loadPermissions();
        this.loadEnables();
    }

    private void loadPermissions() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            if (this.hasNormalizedPermissionsSchema(connection)) {
                try {
                    Int2ObjectMap<Rank> loaded = this.loadPermissionsNormalized(connection);
                    if (loaded != null) {
                        this.publish(loaded);
                        this.normalizedSchemaEnabled = true;
                        LOGGER.info("Permissions Manager -> Using normalized permissions schema.");
                        return;
                    }
                } catch (SQLException e) {
                    LOGGER.warn(
                            "Permissions Manager -> Failed to load normalized permissions schema, falling back to legacy permissions table.",
                            e);
                }
            }

            Int2ObjectMap<Rank> loaded = this.loadPermissionsLegacy(connection);
            this.publish(loaded);
            this.normalizedSchemaEnabled = false;
            LOGGER.info("Permissions Manager -> Using legacy permissions schema.");
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    /** Swaps in a fully loaded rank set and the badge map built from it. */
    private void publish(Int2ObjectMap<Rank> loaded) {
        Map<String, List<Rank>> loadedBadges = new HashMap<>();

        for (Rank rank : loaded.values()) {
            if (rank != null && !rank.getBadge().isEmpty()) {
                loadedBadges
                        .computeIfAbsent(rank.getBadge(), badge -> new ArrayList<>())
                        .add(rank);
            }
        }

        this.ranks = loaded;
        this.badges = loadedBadges;
    }

    /** An existing rank keeps its object, so online users holding it see the reload. */
    private Rank rankFor(int rankId) {
        Rank rank = this.ranks.get(rankId);
        return rank != null ? rank : new Rank(rankId);
    }

    private Int2ObjectMap<Rank> loadPermissionsLegacy(Connection connection) throws SQLException {
        Int2ObjectMap<Rank> loaded = new Int2ObjectOpenHashMap<>();

        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM permissions ORDER BY id ASC")) {
            while (set.next()) {
                Rank rank = this.rankFor(set.getInt("id"));
                rank.load(set);
                loaded.put(rank.getId(), rank);
            }
        }

        return loaded;
    }

    /** The loaded ranks, or null when the normalized tables are empty or incomplete. */
    private Int2ObjectMap<Rank> loadPermissionsNormalized(Connection connection) throws SQLException {
        Int2ObjectMap<Rank> loaded = new Int2ObjectOpenHashMap<>();
        List<Rank> loadedRanks = new ArrayList<>();

        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM permission_ranks ORDER BY id ASC")) {
            while (set.next()) {
                Rank rank = this.rankFor(set.getInt("id"));
                rank.loadNormalizedMetadata(set);
                loaded.put(rank.getId(), rank);
                loadedRanks.add(rank);
            }
        }

        if (loadedRanks.isEmpty()) {
            return null;
        }

        this.ensureNormalizedRankColumns(connection, loadedRanks);

        boolean hasDefinitions = false;
        Map<Rank, Map<String, Permission>> permissions = new HashMap<>();

        for (Rank rank : loadedRanks) {
            permissions.put(rank, new HashMap<>());
        }

        try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM permission_definitions ORDER BY permission_key ASC");
                ResultSet set = statement.executeQuery()) {
            ResultSetMetaData meta = set.getMetaData();
            Set<String> availableColumns = new HashSet<>();

            for (int i = 1; i <= meta.getColumnCount(); i++) {
                availableColumns.add(meta.getColumnName(i).toLowerCase());
            }

            for (Rank rank : loadedRanks) {
                if (!availableColumns.contains(("rank_" + rank.getId()).toLowerCase())) {
                    return null;
                }
            }

            while (set.next()) {
                hasDefinitions = true;
                String permissionKey = set.getString("permission_key");

                for (Rank rank : loadedRanks) {
                    PermissionSetting setting =
                            PermissionSetting.fromString(Integer.toString(set.getInt("rank_" + rank.getId())));
                    permissions.get(rank).put(permissionKey, new Permission(permissionKey, setting));
                }
            }
        }

        if (!hasDefinitions) {
            return null;
        }

        for (Rank rank : loadedRanks) {
            rank.replacePermissions(permissions.get(rank));
        }

        return loaded;
    }

    private void ensureNormalizedRankColumns(Connection connection, List<Rank> loadedRanks) throws SQLException {
        Set<String> availableColumns = new HashSet<>();

        try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'permission_definitions'");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                availableColumns.add(set.getString("column_name").toLowerCase());
            }
        }

        for (Rank rank : loadedRanks) {
            String rankColumn = "rank_" + rank.getId();

            if (availableColumns.contains(rankColumn.toLowerCase())) {
                continue;
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE permission_definitions ADD COLUMN `" + rankColumn
                        + "` tinyint(3) unsigned NOT NULL DEFAULT 0");
            }

            availableColumns.add(rankColumn.toLowerCase());
            LOGGER.info("Permissions Manager -> Added missing normalized permission column {}.", rankColumn);
        }
    }

    private boolean hasNormalizedPermissionsSchema(Connection connection) throws SQLException {
        if (!this.tableExists(connection, "permission_ranks")
                || !this.tableExists(connection, "permission_definitions")) {
            return false;
        }

        if (!this.tableHasRows(connection, "permission_ranks")) {
            return false;
        }

        return this.tableHasRows(connection, "permission_definitions");
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            statement.setString(1, tableName);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() && set.getInt(1) > 0;
            }
        }
    }

    private boolean tableHasRows(Connection connection, String tableName) throws SQLException {
        if (!tableName.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
            throw new SQLException("Refusing to query unsafe table name: " + tableName);
        }

        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return set.next() && set.getInt(1) > 0;
        }
    }

    private void loadEnables() {
        Int2IntMap loaded = new Int2IntOpenHashMap();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM special_enables")) {
            while (set.next()) {
                loaded.put(set.getInt("effect_id"), set.getInt("min_rank"));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return;
        }

        this.enables = loaded;
    }

    public boolean rankExists(int rankId) {
        return this.ranks.containsKey(rankId);
    }

    public Rank getRank(int rankId) {
        return this.ranks.get(rankId);
    }

    public Rank getRankByName(String rankName) {
        for (Rank rank : this.ranks.values()) {
            if (rank.getName().equalsIgnoreCase(rankName)) return rank;
        }

        return null;
    }

    public boolean isEffectBlocked(int effectId, int rank) {
        Int2IntMap current = this.enables;
        return current.containsKey(effectId) && current.get(effectId) > rank;
    }

    public boolean hasPermission(Habbo habbo, String permission) {
        return this.hasPermission(habbo, permission, false);
    }

    public boolean hasPermission(Habbo habbo, String permission, boolean withRoomRights) {
        if (habbo == null || habbo.getHabboInfo() == null || permission == null || permission.isBlank()) {
            return false;
        }

        if (!this.hasPermission(habbo.getHabboInfo().getRank(), permission, withRoomRights)) {
            for (HabboPlugin plugin : Emulator.getPluginManager().getPlugins()) {
                if (plugin.hasPermission(habbo, permission)) {
                    return true;
                }
            }

            return false;
        }

        return true;
    }

    public boolean hasPermission(Rank rank, String permission, boolean withRoomRights) {
        return rank != null
                && permission != null
                && !permission.isBlank()
                && rank.hasPermission(permission, withRoomRights);
    }

    public Set<String> getStaffBadges() {
        return Collections.unmodifiableSet(new HashSet<>(this.badges.keySet()));
    }

    public List<Rank> getRanksByBadgeCode(String code) {
        List<Rank> ranks = this.badges.get(code);
        return ranks == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(ranks));
    }

    public List<Rank> getAllRanks() {
        return new ArrayList<>(this.ranks.values());
    }

    public boolean isNormalizedSchemaEnabled() {
        return this.normalizedSchemaEnabled;
    }
}
