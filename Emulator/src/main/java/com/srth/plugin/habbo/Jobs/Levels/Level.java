package com.srth.plugin.habbo.Jobs.Levels;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Level {

    private int level;
    private int XP;

    public Level(ResultSet resultset) throws SQLException {
        this.level = resultset.getInt("level");
        this.XP = resultset.getInt("XP");
    }

    public int getLevel(){
        return this.level;
    }
    public int getXP(){
        return this.XP;
    }


}
