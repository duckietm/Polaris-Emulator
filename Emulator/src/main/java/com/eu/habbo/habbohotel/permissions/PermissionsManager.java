package com.eu.habbo.habbohotel.permissions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.HabboPlugin;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PermissionsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionsManager.class);

    private final TIntObjectHashMap<Rank> ranks;
    private final TIntIntHashMap enables;
    private final THashMap<String, List<Rank>> badges;
    private volatile boolean normalizedSchemaEnabled;

    public PermissionsManager() {
        long millis = System.currentTimeMillis();
        this.ranks = new TIntObjectHashMap<>();
        this.enables = new TIntIntHashMap();
        this.badges = new THashMap<String, List<Rank>>();

        this.reload();

        LOGGER.info("Permissions Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public void reload() {
        this.loadPermissions();
        this.loadEnables();
    }

    private void loadPermissions() {
        this.badges.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            if (this.hasNormalizedPermissionsSchema(connection)) {
                try {
                    if (this.loadPermissionsNormalized(connection)) {
                        this.normalizedSchemaEnabled = true;
                        LOGGER.info("Permissions Manager -> Using normalized permissions schema.");
                        return;
                    }
                } catch (SQLException e) {
                    LOGGER.warn("Permissions Manager -> Failed to load normalized permissions schema, falling back to legacy permissions table.", e);
                }
            }

            this.normalizedSchemaEnabled = false;
            this.badges.clear();
            LOGGER.info("Permissions Manager -> Using legacy permissions schema.");
            this.loadPermissionsLegacy(connection);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private void loadPermissionsLegacy(Connection connection) throws SQLException {
        Set<Integer> loadedRankIds = new HashSet<>();

        try (Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM permissions ORDER BY id ASC")) {
            while (set.next()) {
                int rankId = set.getInt("id");
                loadedRankIds.add(rankId);

                Rank rank = null;
                if (!this.ranks.containsKey(rankId)) {
                    rank = new Rank(set);
                    this.ranks.put(rankId, rank);
                } else {
                    rank = this.ranks.get(rankId);
                    rank.load(set);
                }

                this.addBadgeMapping(rank);
            }
        }

        this.pruneMissingRanks(loadedRankIds);
    }

    private boolean loadPermissionsNormalized(Connection connection) throws SQLException {
        boolean hasRanks = false;
        List<Rank> loadedRanks = new ArrayList<>();
        Set<Integer> loadedRankIds = new HashSet<>();

        try (Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM permission_ranks ORDER BY id ASC")) {
            while (set.next()) {
                hasRanks = true;
                int rankId = set.getInt("id");
                loadedRankIds.add(rankId);

                Rank rank = this.ranks.get(rankId);

                if (rank == null) {
                    rank = new Rank(rankId);
                    this.ranks.put(rankId, rank);
                }

                rank.loadNormalizedMetadata(set);
                this.addBadgeMapping(rank);
                loadedRanks.add(rank);
            }
        }

        if (!hasRanks) {
            return false;
        }

        this.ensureNormalizedRankColumns(connection, loadedRanks);

        boolean hasDefinitions = false;

        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM permission_definitions ORDER BY permission_key ASC");
             ResultSet set = statement.executeQuery()) {
            ResultSetMetaData meta = set.getMetaData();
            Set<String> availableColumns = new HashSet<>();

            for (int i = 1; i <= meta.getColumnCount(); i++) {
                availableColumns.add(meta.getColumnName(i).toLowerCase());
            }

            for (Rank rank : loadedRanks) {
                if (!availableColumns.contains(("rank_" + rank.getId()).toLowerCase())) {
                    return false;
                }
            }

            while (set.next()) {
                hasDefinitions = true;
                String permissionKey = set.getString("permission_key");

                for (Rank rank : loadedRanks) {
                    String rankColumn = "rank_" + rank.getId();

                    if (!availableColumns.contains(rankColumn.toLowerCase())) {
                        continue;
                    }

                    rank.setPermission(permissionKey, PermissionSetting.fromString(Integer.toString(set.getInt(rankColumn))));
                }
            }
        }

        this.pruneMissingRanks(loadedRankIds);
        return hasDefinitions;
    }

    private void pruneMissingRanks(Set<Integer> loadedRankIds) {
        for (int rankId : this.ranks.keys()) {
            if (!loadedRankIds.contains(rankId)) {
                this.ranks.remove(rankId);
            }
        }
    }

    private void ensureNormalizedRankColumns(Connection connection, List<Rank> loadedRanks) throws SQLException {
        Set<String> availableColumns = new HashSet<>();

        try (PreparedStatement statement = connection.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'permission_definitions'");
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
                statement.execute("ALTER TABLE permission_definitions ADD COLUMN `" + rankColumn + "` tinyint(3) unsigned NOT NULL DEFAULT 0");
            }

            availableColumns.add(rankColumn.toLowerCase());
            LOGGER.info("Permissions Manager -> Added missing normalized permission column {}.", rankColumn);
        }
    }

    private boolean hasNormalizedPermissionsSchema(Connection connection) throws SQLException {
        if (!this.tableExists(connection, "permission_ranks") || !this.tableExists(connection, "permission_definitions")) {
            return false;
        }

        if (!this.tableHasRows(connection, "permission_ranks")) {
            return false;
        }

        return this.tableHasRows(connection, "permission_definitions");
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            statement.setString(1, tableName);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() && set.getInt(1) > 0;
            }
        }
    }

    private boolean tableHasRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return set.next() && set.getInt(1) > 0;
        }
    }

    private void addBadgeMapping(Rank rank) {
        if (rank != null && !rank.getBadge().isEmpty()) {
            if (!this.badges.containsKey(rank.getBadge())) {
                this.badges.put(rank.getBadge(), new ArrayList<Rank>());
            }

            this.badges.get(rank.getBadge()).add(rank);
        }
    }

    private void loadEnables() {
        synchronized (this.enables) {
            this.enables.clear();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM special_enables")) {
                while (set.next()) {
                    this.enables.put(set.getInt("effect_id"), set.getInt("min_rank"));
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }


    public boolean rankExists(int rankId) {
        return this.ranks.containsKey(rankId);
    }


    public Rank getRank(int rankId) {
        return this.ranks.get(rankId);
    }


    public Rank getRankByName(String rankName) {
        for (Rank rank : this.ranks.valueCollection()) {
            if (rank.getName().equalsIgnoreCase(rankName))
                return rank;
        }

        return null;
    }


    public boolean isEffectBlocked(int effectId, int rank) {
        return this.enables.contains(effectId) && this.enables.get(effectId) > rank;
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
        return rank != null && permission != null && !permission.isBlank() && rank.hasPermission(permission, withRoomRights);
    }

    public Set<String> getStaffBadges() {
        return Collections.unmodifiableSet(new HashSet<>(this.badges.keySet()));
    }

    public List<Rank> getRanksByBadgeCode(String code) {
        List<Rank> ranks = this.badges.get(code);
        return ranks == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(ranks));
    }

    public List<Rank> getAllRanks() {
        return new ArrayList<>(this.ranks.valueCollection());
    }

    public boolean isNormalizedSchemaEnabled() {
        return this.normalizedSchemaEnabled;
    }
}
