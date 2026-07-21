package com.eu.habbo.habbohotel.soundboard;

import java.sql.ResultSet;
import java.sql.SQLException;

// One soundboard pad: a named audio clip served from a URL (uploaded via the CMS).
public class SoundboardSound {
    public final int id;
    public final String name;
    public final String url;
    public final int minRank;

    public SoundboardSound(ResultSet set) throws SQLException {
        this(set.getInt("id"), set.getString("name"), set.getString("url"), set.getInt("min_rank"));
    }

    public SoundboardSound(int id, String name, String url, int minRank) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.url = url == null ? "" : url;
        this.minRank = Math.max(1, minRank);
    }

    public boolean isAvailableTo(int rankId) {
        return rankId >= this.minRank;
    }
}
