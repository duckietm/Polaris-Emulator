package com.eu.habbo.habbohotel.soundboard;

import java.sql.ResultSet;
import java.sql.SQLException;

// One soundboard pad. Identity and audio file come from the asset pipeline,
// keyed by `classname` (nitro-assets/sounds/soundboard + gamedata/SoundData.json).
// `url` is an optional override for clips hosted outside the asset tree.
public class SoundboardSound {
    public final int id;
    public final String name;
    public final String classname;
    public final String url;
    public final boolean enabled;
    public final int sortOrder;
    public final int minRank;

    public SoundboardSound(ResultSet set) throws SQLException {
        this(
                set.getInt("id"),
                set.getString("name"),
                set.getString("classname"),
                set.getString("url"),
                set.getBoolean("enabled"),
                set.getInt("sort_order"),
                set.getInt("min_rank"));
    }

    public SoundboardSound(int id, String name, String classname, String url, int minRank) {
        this(id, name, classname, url, true, 0, minRank);
    }

    public SoundboardSound(
            int id, String name, String classname, String url, boolean enabled, int sortOrder, int minRank) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.classname = classname == null ? "" : classname;
        this.url = url == null ? "" : url;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.minRank = Math.max(1, minRank);
    }

    public boolean isAvailableTo(int rankId) {
        return rankId >= this.minRank;
    }
}
