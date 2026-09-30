package com.eu.habbo.habbohotel.permissions;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class Rank {

    private final int id;

    private int level;
    // Replaced whole on a reload, never changed in place, so a check never sees a half-filled map.
    private volatile Map<String, Permission> permissions;
    private volatile Map<String, String> variables;
    private String name;
    private String badge;
    private int roomEffect;

    private boolean logCommands;

    private String prefix;

    private String prefixColor;

    private boolean hasPrefix;
    private int diamondsTimerAmount;
    private int creditsTimerAmount;
    private int pixelsTimerAmount;
    private int gotwTimerAmount;
    private int soundboardCooldownSeconds;
    // Limits this rank raises (0 = the hotel setting); see RankLimits.
    private int maxRooms;
    private int maxFriends;
    private int maxFavouriteRooms;

    public Rank(ResultSet set) throws SQLException {
        this(set.getInt("id"));
        this.load(set);
    }

    public Rank(int id) {
        this.permissions = new HashMap<>();
        this.variables = new HashMap<>();
        this.id = id;
        this.level = 1;
        this.diamondsTimerAmount = 1;
        this.creditsTimerAmount = 1;
        this.pixelsTimerAmount = 1;
        this.gotwTimerAmount = 1;
        this.soundboardCooldownSeconds = 60;
    }

    public void load(ResultSet set) throws SQLException {
        this.loadMetadata(set);

        Map<String, Permission> loadedPermissions = new HashMap<>();
        Map<String, String> loadedVariables = new HashMap<>();
        ResultSetMetaData meta = set.getMetaData();

        for (int i = 1; i < meta.getColumnCount() + 1; i++) {
            String columnName = meta.getColumnName(i);
            if (columnName.startsWith("cmd_") || columnName.startsWith("acc_")) {
                loadedPermissions.put(
                        columnName, new Permission(columnName, PermissionSetting.fromString(set.getString(i))));
            } else {
                loadedVariables.put(columnName, set.getString(i));
            }
        }

        this.permissions = loadedPermissions;
        this.variables = loadedVariables;
    }

    /** Metadata only; the permissions arrive afterwards through {@link #replacePermissions}. */
    public void loadNormalizedMetadata(ResultSet set) throws SQLException {
        this.loadMetadata(set);
        this.storeMetadataVariables();
    }

    /** Swaps in a complete permission set in one step. */
    public void replacePermissions(Map<String, Permission> loadedPermissions) {
        this.permissions = new HashMap<>(loadedPermissions);
    }

    public void setPermission(String key, PermissionSetting setting) {
        Map<String, Permission> updated = new HashMap<>(this.permissions);
        updated.put(key, new Permission(key, setting));
        this.permissions = updated;
    }

    private void loadMetadata(ResultSet set) throws SQLException {
        this.name = this.safeString(set.getString("rank_name"));
        this.badge = this.safeString(set.getString("badge"));
        this.roomEffect = set.getInt("room_effect");
        this.logCommands = "1".equals(this.safeString(set.getString("log_commands")));
        this.prefix = this.safeString(set.getString("prefix"));
        this.prefixColor = this.safeString(set.getString("prefix_color"));
        this.level = set.getInt("level");
        this.diamondsTimerAmount = set.getInt("auto_points_amount");
        this.creditsTimerAmount = set.getInt("auto_credits_amount");
        this.pixelsTimerAmount = set.getInt("auto_pixels_amount");
        this.gotwTimerAmount = set.getInt("auto_gotw_amount");
        int loadedSoundboardCooldown = set.getInt("soundboard_cooldown_seconds");
        this.soundboardCooldownSeconds = set.wasNull() || loadedSoundboardCooldown < 0 ? 60 : loadedSoundboardCooldown;
        this.hasPrefix = !this.prefix.isEmpty();
        this.maxRooms = optionalInt(set, "max_rooms");
        this.maxFriends = optionalInt(set, "max_friends");
        this.maxFavouriteRooms = optionalInt(set, "max_favourite_rooms");
    }

    /** A limit column, or 0 when the table (an older schema, the legacy one) does not have it. */
    private static int optionalInt(ResultSet set, String column) {
        try {
            set.findColumn(column);
            return Math.max(0, set.getInt(column));
        } catch (SQLException e) {
            return 0;
        }
    }

    private void storeMetadataVariables() {
        Map<String, String> variables = new HashMap<>();
        variables.put("id", Integer.toString(this.id));
        variables.put("rank_name", this.name);
        variables.put("badge", this.badge);
        variables.put("room_effect", Integer.toString(this.roomEffect));
        variables.put("log_commands", this.logCommands ? "1" : "0");
        variables.put("prefix", this.prefix);
        variables.put("prefix_color", this.prefixColor);
        variables.put("level", Integer.toString(this.level));
        variables.put("auto_points_amount", Integer.toString(this.diamondsTimerAmount));
        variables.put("auto_credits_amount", Integer.toString(this.creditsTimerAmount));
        variables.put("auto_pixels_amount", Integer.toString(this.pixelsTimerAmount));
        variables.put("auto_gotw_amount", Integer.toString(this.gotwTimerAmount));
        variables.put("soundboard_cooldown_seconds", Integer.toString(this.soundboardCooldownSeconds));
        this.variables = variables;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    public boolean hasPermission(String key, boolean isRoomOwner) {
        if (key == null || key.isBlank()) {
            return false;
        }

        Permission permission = this.permissions.get(key);

        if (permission == null) {
            return false;
        }

        return permission.setting == PermissionSetting.ALLOWED
                || permission.setting == PermissionSetting.ROOM_OWNER && isRoomOwner;
    }

    public int getId() {
        return this.id;
    }

    public int getLevel() {
        return this.level;
    }

    public String getName() {
        return this.name;
    }

    public String getBadge() {
        return this.badge;
    }

    public Map<String, Permission> getPermissions() {
        return this.permissions;
    }

    public Map<String, String> getVariables() {
        return this.variables;
    }

    public int getRoomEffect() {
        return this.roomEffect;
    }

    public boolean isLogCommands() {
        return this.logCommands;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public String getPrefixColor() {
        return this.prefixColor;
    }

    public boolean hasPrefix() {
        return this.hasPrefix;
    }

    public int getDiamondsTimerAmount() {
        return this.diamondsTimerAmount;
    }

    public int getCreditsTimerAmount() {
        return this.creditsTimerAmount;
    }

    public int getPixelsTimerAmount() {
        return this.pixelsTimerAmount;
    }

    public int getGotwTimerAmount() {
        return this.gotwTimerAmount;
    }

    public int getMaxRooms() {
        return this.maxRooms;
    }

    public int getMaxFriends() {
        return this.maxFriends;
    }

    public int getMaxFavouriteRooms() {
        return this.maxFavouriteRooms;
    }

    /** For tests and plugins; a reload replaces them from permission_ranks. */
    public void setLimits(int maxRooms, int maxFriends, int maxFavouriteRooms) {
        this.maxRooms = Math.max(0, maxRooms);
        this.maxFriends = Math.max(0, maxFriends);
        this.maxFavouriteRooms = Math.max(0, maxFavouriteRooms);
    }

    public int getSoundboardCooldownSeconds() {
        return this.soundboardCooldownSeconds;
    }
}
