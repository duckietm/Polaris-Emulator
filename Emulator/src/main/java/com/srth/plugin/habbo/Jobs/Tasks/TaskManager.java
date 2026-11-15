package com.srth.plugin.habbo.Jobs.Tasks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;

import gnu.trove.map.hash.THashMap;

public class TaskManager {

    private THashMap<Integer, Task> tasks = new THashMap<Integer,Task>();
    public TaskManager() {

    }
    public void updateTasks(){
        tasks.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_jobs_tasks"))
        {
            try(ResultSet resultSet = statement.executeQuery()){
                while (resultSet.next()){
                    tasks.put(resultSet.getInt("id"),new Task(resultSet));
                }
                resultSet.close();
                statement.close();
            }

        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    public THashMap<Integer,Task> getTasksForPlayer(LevelPlayer player) throws SQLException {
        THashMap<Integer,Task> tasksToDo = new THashMap<Integer,Task>();
        for(int taskId : tasks.keySet()){
            if(player.getPlayerInfo().getTasksForWork().contains(taskId)){
                if(!player.getPlayerInfo().getTasksDone().contains(taskId)){
                    tasksToDo.put(taskId,tasks.get(taskId));
                }
            }
        }
        return tasksToDo;
    }


    public THashMap<Integer, Task> getTasks(){
        return this.tasks;
    }

    public THashMap<Integer,Task> getTaskActive(LevelPlayer player) throws SQLException {
        
        THashMap<Integer,Task> tasksActives = new THashMap<Integer,Task>();
        for(int taskId : tasks.keySet()){
            if(player.getPlayerInfo().getTaskActive().contains(taskId)){
                tasksActives.put(taskId,tasks.get(taskId));
            }
        }

        return tasksActives;

    }

}
