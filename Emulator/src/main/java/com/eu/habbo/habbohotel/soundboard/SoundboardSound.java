package com.eu.habbo.habbohotel.soundboard;

import java.sql.ResultSet;
import java.sql.SQLException;

// One soundboard pad: a named audio clip served from a URL (uploaded via the CMS).
public class SoundboardSound {
    public final int id;
    public final String name;
    public final String url;

    public SoundboardSound(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name");
        this.url = set.getString("url");
    }
}
