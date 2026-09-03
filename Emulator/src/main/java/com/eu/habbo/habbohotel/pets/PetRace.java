package com.eu.habbo.habbohotel.pets;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PetRace {

    public final int race;

    public final int colorOne;

    public final int colorTwo;

    public final boolean hasColorOne;

    public final boolean hasColorTwo;

    public final boolean clubOnly;

    public PetRace(ResultSet set) throws SQLException {
        this.race = set.getInt("race");
        this.colorOne = set.getInt("color_one");
        this.colorTwo = set.getInt("color_two");
        this.hasColorOne = set.getString("has_color_one").equals("1");
        this.hasColorTwo = set.getString("has_color_two").equals("1");

        boolean clubOnly = false;
        try {
            clubOnly = "1".equals(set.getString("club_only"));
        } catch (SQLException ignored) {
        }
        this.clubOnly = clubOnly;
    }
}
