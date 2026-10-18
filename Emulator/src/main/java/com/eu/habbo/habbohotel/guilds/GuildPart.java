package com.eu.habbo.habbohotel.guilds;

import java.sql.ResultSet;
import java.sql.SQLException;

public class GuildPart {

    public final int id;

    public final String valueA;

    public final String valueB;

    public GuildPart(int id, String valueA, String valueB) {
        this.id = id;
        this.valueA = valueA;
        this.valueB = valueB;
    }

    public GuildPart(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.valueA = set.getString("firstvalue");
        this.valueB = set.getString("secondvalue");
    }
}
