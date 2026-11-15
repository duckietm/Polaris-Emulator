package com.srth.plugin.habbo.Jobs.Player.PlayerInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.WorkSystemLevelManager;

public class PlayerInfo {

    private int userid;
    private String job;
    private int level;
    private int XP;

    public PlayerInfo(ResultSet resultSet) throws SQLException {
        this.userid = resultSet.getInt("user_id"); //userid (habboId)
        this.job = resultSet.getString("job");
        this.level = resultSet.getInt("level");
        this.XP = resultSet.getInt("xp");
    }

    public Habbo getHabbo(){
        return Emulator.getGameEnvironment().getHabboManager().getHabbo(this.userid);
    }
    public int getLevel(){
        return this.level;
    }
    public String getJob(){
        return this.job;
    }
    public int getXP() {
        return this.XP;
    }

    public void LevelUP(){
        this.level+=1;
    }

    public List<Integer> getTasksForWork() throws SQLException {
        List<Integer> tasksIds = new ArrayList<>();

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(getHabbo());

        if(habboRp.getJobActive() != null){
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement st = connection.prepareStatement("SELECT * FROM rp_jobs_tasks WHERE enabled = ? AND job_name = ?")) {
                    st.setInt(1, 1);
                    st.setString(2, habboRp.getJobActive().getName());
                    ResultSet resultSet = st.executeQuery();
                    while (resultSet.next()) {
                        tasksIds.add(resultSet.getInt("id"));
                    }
                    st.close();
                    resultSet.close();
                }
            }
        }

        return tasksIds;
    }

    public List<Integer> getTasksDone() throws SQLException {
        List<Integer> tasksIds = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement st = connection.prepareStatement("SELECT * FROM rp_jobs_tasks_users_process WHERE user_id = ? AND status='success'")) {
                st.setInt(1, this.userid);
                ResultSet resultSet = st.executeQuery();
                while (resultSet.next()) {
                    tasksIds.add(resultSet.getInt("task_id"));
                }
                st.close();
                resultSet.close();
            }
        }
        return tasksIds;
    }

    public void addTaskProcess(int taskId, int process){
        try(Connection conn = Emulator.getDatabase().getDataSource().getConnection()){
            try(PreparedStatement statement = conn.prepareStatement("UPDATE rp_jobs_tasks_users_process SET process = ? WHERE user_id = ? AND task_id = ?")){
                statement.setInt(1,process);
                statement.setInt(2,this.getHabbo().getHabboInfo().getId());
                statement.setInt(3,taskId);
                statement.executeUpdate();
                statement.close();
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    public void addTaskDone(int taskId){
        try(Connection conn = Emulator.getDatabase().getDataSource().getConnection()){
            try(PreparedStatement statement = conn.prepareStatement("UPDATE rp_jobs_tasks_users_process SET status = ? WHERE user_id = ? AND task_id = ?")){
                statement.setString(1,"success");
                statement.setInt(2,this.getHabbo().getHabboInfo().getId());
                statement.setInt(3, taskId);
                statement.executeUpdate();
                statement.close();
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    
    public List<Integer> getTaskActive() throws SQLException {
        List<Integer> tasksIds = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement st = connection.prepareStatement("SELECT * FROM rp_jobs_tasks_users_process WHERE active = ? AND user_id= ?")) {
                st.setInt(1, 1);
                st.setInt(2, this.getHabbo().getHabboInfo().getId());
                ResultSet resultSet = st.executeQuery();
                while (resultSet.next()) {
                    tasksIds.add(resultSet.getInt("task_id"));
                }
                st.close();
                resultSet.close();
            }
        }
        return tasksIds;
    }

    public void addXP(int XP){
        this.XP += XP;

        if (this.XP >= WorkSystemLevelManager.getLevelManager().Levels().get(this.level).getXP()) {
            
            if (WorkSystemLevelManager.getLevelManager().Levels().get(this.level + 1) != null) {
                this.LevelUP();
            }

            this.update();
            
        } else {
            this.update();
        }
    }

    public void update(){
        try(Connection conn = Emulator.getDatabase().getDataSource().getConnection()){

            try(PreparedStatement statement = conn.prepareStatement("UPDATE rp_jobs_tasks_users_levels SET level = ? , xp = ? WHERE job = ? AND user_id = ? LIMIT 1")){
                statement.setInt(1,this.level);
                statement.setInt(2,this.XP);
                statement.setString(3, this.job);
                statement.setInt(4,this.getHabbo().getHabboInfo().getId());
                statement.executeUpdate();
                statement.close();
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }



}
