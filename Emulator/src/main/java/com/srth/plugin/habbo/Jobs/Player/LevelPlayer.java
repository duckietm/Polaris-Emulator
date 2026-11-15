package com.srth.plugin.habbo.Jobs.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.WorkSystemLevelManager;
import com.srth.plugin.habbo.Jobs.Player.PlayerInfo.PlayerInfo;
import com.srth.plugin.habbo.Jobs.Player.PlayerInfo.PlayerStats;
import com.srth.plugin.habbo.Jobs.Tasks.Task;

import gnu.trove.map.hash.THashMap;

public class LevelPlayer {

    private final PlayerInfo playerInfo;
    private PlayerStats playerStats;
    private THashMap<Integer, Task> tasks = new THashMap<>();
    private THashMap<Integer, Task> taskActive = new THashMap<>();

    public LevelPlayer(ResultSet set) throws SQLException {
        this.playerInfo = new PlayerInfo(set);

        this.tasks = WorkSystemLevelManager.getTaskManager().getTasksForPlayer(this);

        this.taskActive = WorkSystemLevelManager.getTaskManager().getTaskActive(this);
    }

    public void setPlayerStats(int user_id, int task_id) throws SQLException {
        this.playerStats = new PlayerStats(user_id, this, task_id);
    }

    public PlayerInfo getPlayerInfo() {
        return playerInfo;
    }

    public PlayerStats getPlayerStats() {
        return playerStats;
    }

    public void createTaskActive(int task_id) throws SQLException {
        boolean existe = false;
        int habboId = this.getPlayerInfo().getHabbo().getHabboInfo().getId();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM rp_jobs_tasks_users_process WHERE task_id = ? AND user_id = ? LIMIT 1")) {
                preparedStatement.setInt(1, task_id);
                preparedStatement.setInt(2, habboId);
                ResultSet set = preparedStatement.executeQuery();
                while (set.next()) {
                    existe = true;
                }
                preparedStatement.close();
                set.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (existe == false) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement preparedStatement = connection.prepareStatement(
                        "INSERT INTO rp_jobs_tasks_users_process(user_id,active,task_id,process,ultimo_timestamp,status) VALUES (?,?,?,?,?,?);")) {
                    preparedStatement.setInt(1, habboId);
                    preparedStatement.setInt(2, 1);
                    preparedStatement.setInt(3, task_id);
                    preparedStatement.setInt(4, 0);
                    preparedStatement.setInt(5, Emulator.getIntUnixTimestamp());
                    preparedStatement.setString(6, "incomplete");
                    preparedStatement.executeUpdate();
                    preparedStatement.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement statement = conn.prepareStatement(
                        "UPDATE rp_jobs_tasks_users_process SET status = ? , active = ? WHERE user_id = ? AND task_id = ? LIMIT 1")) {
                    statement.setString(1, "incomplete");
                    statement.setInt(2, 1);
                    statement.setInt(3, habboId);
                    statement.setInt(4, task_id);
                    statement.executeUpdate();
                    statement.close();
                }
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
    }

    public static LevelPlayer getLevelPlayer(Habbo habbo) throws SQLException {

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        int habboId = habbo.getHabboInfo().getId();

        String job_name = "";
        if (habboRp.getJobActive() != null) {
            job_name = habboRp.getJobActive().getName();
        }

        LevelPlayer player = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM rp_jobs_tasks_users_levels WHERE job = ? AND user_id = ? LIMIT 1")) {
                preparedStatement.setString(1, job_name);
                preparedStatement.setInt(2, habboId);
                ResultSet set = preparedStatement.executeQuery();
                while (set.next()) {
                    player = new LevelPlayer(set);
                }
                preparedStatement.close();
                set.close();
                return player;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void createLevelPlayer(Habbo habbo) {

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            String job_name = "";
            if (habboRp.getJobActive() != null) {
                job_name = habboRp.getJobActive().getName();
            }
            int habboId = habbo.getHabboInfo().getId();

            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "INSERT INTO rp_jobs_tasks_users_levels(user_id,job,level,xp) VALUES (?,?,?,?);")) {
                preparedStatement.setInt(1, habboId);
                preparedStatement.setString(2, job_name);
                preparedStatement.setInt(3, 1);
                preparedStatement.setInt(4, 0);
                preparedStatement.executeUpdate();
                preparedStatement.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public THashMap<Integer, Task> getTasks() {
        return this.tasks;
    }

    public THashMap<Integer, Task> getTaskActive() {
        return this.taskActive;
    }

    public void updateTasks() throws SQLException {
        this.tasks = WorkSystemLevelManager.getTaskManager().getTasksForPlayer(this);
    }

    public void updateTasksActive() throws SQLException {
        this.taskActive.clear();
        this.taskActive = WorkSystemLevelManager.getTaskManager().getTaskActive(this);
    }

    public void resetTasksActive() {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = conn
                    .prepareStatement("UPDATE rp_jobs_tasks_users_process SET active = ? WHERE user_id = ?")) {
                statement.setInt(1, 0);
                statement.setInt(2, this.getPlayerInfo().getHabbo().getHabboInfo().getId());
                statement.executeUpdate();
                statement.close();
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    public void generateTask(int task_id) throws SQLException {

        int operacion = task_id - 1;

        THashMap<Integer, Task> randomtask = WorkSystemLevelManager.getTaskManager().getTasksForPlayer(this);
        ArrayList<Integer> ids = new ArrayList<Integer>(randomtask.keySet());
        int total = randomtask.keySet().size();

        if (total >= 1) {
            int total_id = ids.get(operacion);
            this.createTaskActive(total_id);
            this.updateTasksActive();
        }
    }

    public void generateRandomTask() throws SQLException {

        THashMap<Integer, Task> randomtask = WorkSystemLevelManager.getTaskManager().getTasksForPlayer(this);
        ArrayList<Integer> ids = new ArrayList<Integer>(randomtask.keySet());
        int total = randomtask.keySet().size();

        if (total >= 1) {
            int random = Emulator.getRandom().nextInt(total);
            int total_id = ids.get(random);

            this.createTaskActive(total_id);
            this.updateTasksActive();
        }
    }

    public int getAmountTaskActive() throws SQLException {

        THashMap<Integer, Task> taskactives = WorkSystemLevelManager.getTaskManager().getTaskActive(this);
        int total = taskactives.keySet().size();

        return total;

    }

}
