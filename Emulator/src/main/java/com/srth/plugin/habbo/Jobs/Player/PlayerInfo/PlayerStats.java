package com.srth.plugin.habbo.Jobs.Player.PlayerInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;

public class PlayerStats {

    private int user_id;
    private int process;

    public PlayerStats(int habboId, LevelPlayer player, int task_id) throws SQLException {
        try(Connection conn = Emulator.getDatabase().getDataSource().getConnection()){
            try(PreparedStatement st = conn.prepareStatement("SELECT * FROM rp_jobs_tasks_users_process WHERE task_id = " + task_id + " AND user_id = " + habboId + " LIMIT 1")) {
                ResultSet set = st.executeQuery();
                while (set.next()) {
                    this.user_id = set.getInt("user_id");
                    this.process = set.getInt("process");
                }
            }
        } 
    }

    public int getUserId() {
        return user_id;
    }

    public int getProcess(){
        return process;
    }


}
