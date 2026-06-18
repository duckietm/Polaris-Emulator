package com.eu.habbo.habbohotel.permissions;

import gnu.trove.map.hash.THashMap;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

public class Rank {

    private final int id;


    private int level;
    private final THashMap<String, Permission> permissions;
    private final THashMap<String, String> variables;
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

    public Rank(ResultSet set) throws SQLException {
        this(set.getInt("id"));
        this.load(set);
    }

    public Rank(int id) {
        this.permissions = new THashMap<>();
        this.variables = new THashMap<>();
        this.id = id;
        this.level = 1;
        this.diamondsTimerAmount = 1;
        this.creditsTimerAmount = 1;
        this.pixelsTimerAmount = 1;
        this.gotwTimerAmount = 1;
    }

    public void load(ResultSet set) throws SQLException {
        this.permissions.clear();
        this.variables.clear();

        this.loadMetadata(set);

        ResultSetMetaData meta = set.getMetaData();

        for (int i = 1; i < meta.getColumnCount() + 1; i++) {
            String columnName = meta.getColumnName(i);
            if (columnName.startsWith("cmd_") || columnName.startsWith("acc_")) {
                this.permissions.put(meta.getColumnName(i), new Permission(columnName, PermissionSetting.fromString(set.getString(i))));
            } else {
                this.variables.put(meta.getColumnName(i), set.getString(i));
            }
        }
    }

    public void loadNormalizedMetadata(ResultSet set) throws SQLException {
        this.permissions.clear();
        this.variables.clear();
        this.loadMetadata(set);
        this.storeMetadataVariables();
    }

    public void setPermission(String key, PermissionSetting setting) {
        this.permissions.put(key, new Permission(key, setting));
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
        this.hasPrefix = !this.prefix.isEmpty();
    }

    private void storeMetadataVariables() {
        this.variables.put("id", Integer.toString(this.id));
        this.variables.put("rank_name", this.name);
        this.variables.put("badge", this.badge);
        this.variables.put("room_effect", Integer.toString(this.roomEffect));
        this.variables.put("log_commands", this.logCommands ? "1" : "0");
        this.variables.put("prefix", this.prefix);
        this.variables.put("prefix_color", this.prefixColor);
        this.variables.put("level", Integer.toString(this.level));
        this.variables.put("auto_points_amount", Integer.toString(this.diamondsTimerAmount));
        this.variables.put("auto_credits_amount", Integer.toString(this.creditsTimerAmount));
        this.variables.put("auto_pixels_amount", Integer.toString(this.pixelsTimerAmount));
        this.variables.put("auto_gotw_amount", Integer.toString(this.gotwTimerAmount));
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    public boolean hasPermission(String key, boolean isRoomOwner) {
        if (key == null || key.isBlank()) {
            return false;
        }

        if (this.permissions.containsKey(key)) {
            Permission permission = this.permissions.get(key);

            return permission.setting == PermissionSetting.ALLOWED || permission.setting == PermissionSetting.ROOM_OWNER && isRoomOwner;

        }

        return false;
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

    public THashMap<String, Permission> getPermissions() {
        return this.permissions;
    }

    public THashMap<String, String> getVariables() {
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

    public int getDiamondsTimerAmount() { return this.diamondsTimerAmount; }

    public int getCreditsTimerAmount() { return this.creditsTimerAmount; }

    public int getPixelsTimerAmount() { return this.pixelsTimerAmount; }

    public int getGotwTimerAmount() { return this.gotwTimerAmount; }
}

