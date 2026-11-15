package com.srth.plugin.habbo.Jobs.Levels;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;

import gnu.trove.map.hash.THashMap;

public class LevelManager {
    private THashMap<Integer,Level> levels = new THashMap<>();
    public LevelManager() throws SQLException {
    }

    public void updateLevels() throws SQLException {
        this.levels.clear();
        try(Connection conn = Emulator.getDatabase().getDataSource().getConnection()){
            try(PreparedStatement preparedStatement = conn.prepareStatement("SELECT * FROM rp_jobs_tasks_levels")){
                ResultSet set = preparedStatement.executeQuery();
                while (set.next()){
                    this.levels.put(set.getInt("level"), 
                    new Level(set));
                }
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
    }
    public  THashMap<Integer, Level> Levels(){
        return this.levels;
    }
}
