package com.eu.habbo.habbohotel.modtool;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WordFilterWord {
    public final String key;
    public final String replacement;
    public final boolean hideMessage;
    public final boolean autoReport;
    public final int muteTime;
    public final boolean prefixOnly;

    public WordFilterWord(ResultSet set) throws SQLException {
        this.key = set.getString("key");
        this.replacement = set.getString("replacement");
        this.hideMessage = set.getInt("hide") == 1;
        this.autoReport = set.getInt("report") == 1;
        this.muteTime = set.getInt("mute");
        this.prefixOnly = readBooleanColumn(set, "prefix_only");
    }

    public WordFilterWord(String key, String replacement) {
        this(key, replacement, false);
    }

    public WordFilterWord(String key, String replacement, boolean prefixOnly) {
        this.key = key;
        this.replacement = replacement;
        this.hideMessage = false;
        this.autoReport = false;
        this.muteTime = 0;
        this.prefixOnly = prefixOnly;
    }

    private static boolean readBooleanColumn(ResultSet set, String column) {
        try {
            return set.getInt(column) == 1;
        } catch (SQLException e) {
            return false;
        }
    }
}
