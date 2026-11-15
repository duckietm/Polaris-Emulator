package com.srth.plugin.habbo.Jobs;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.events.furniture.FurnitureToggleEvent;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;
import com.srth.plugin.habbo.Jobs.Levels.LevelManager;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.habbo.Jobs.Tasks.Task;
import com.srth.plugin.habbo.Jobs.Tasks.TaskManager;

import gnu.trove.map.hash.THashMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;

public class WorkSystemLevelManager {

    private static THashMap<Integer, LevelPlayer> onlinePlayers = new THashMap<Integer, LevelPlayer>();
    private static TaskManager taskManager;
    private static LevelManager levelManager;

    private static THashMap<Integer, Short> itemListX = new THashMap<Integer, Short>();
    private static THashMap<Integer, Short> itemListY = new THashMap<Integer, Short>();

    public WorkSystemLevelManager() throws SQLException {
        taskManager = new TaskManager();
        levelManager = new LevelManager();
        taskManager.updateTasks();
        levelManager.updateLevels();
    }

    public void setCasillasItem(HabboItem habboItem) {
        int width = habboItem.getBaseItem().getWidth();
        int length = habboItem.getBaseItem().getLength();

        // Add a las listas la cantidad de casillas que ocupa el furni
        for (int i = 0; i < width; i++) {
            short newX = (short) ((short) habboItem.getX() + i);
            itemListX.put(habboItem.getX() + i, newX);
        }
        for (int i = 0; i < length; i++) {
            short newY = (short) ((short) habboItem.getY() + i);
            itemListY.put(habboItem.getY() + i, newY);
        }
    }

    public static void addOnlinePlayer(LevelPlayer player) {
        onlinePlayers.put(player.getPlayerInfo().getHabbo().getHabboInfo().getId(), player);
    }

    public static LinkedHashMap<Integer, Integer> getTopListExperimentados(String work_name) throws SQLException {
        LinkedHashMap<Integer, Integer> topList = new LinkedHashMap<>(); // habboId, Level
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement preparedStatement = conn.prepareStatement(
                    "SELECT * FROM rp_jobs_tasks_users_levels WHERE job = ? ORDER BY level DESC LIMIT 3;")) {
                preparedStatement.setString(1, work_name);
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    topList.put(resultSet.getInt("user_id"), resultSet.getInt("level"));
                }
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
        return topList;
    }

    public static LinkedHashMap<Integer, Integer> getTopListRecientes(String work_name) throws SQLException {
        LinkedHashMap<Integer, Integer> topList = new LinkedHashMap<>(); // habboId, Level
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement preparedStatement = conn.prepareStatement(
                    "SELECT * FROM rp_jobs_tasks_users_levels WHERE job = ? ORDER BY id DESC LIMIT 3;")) {
                preparedStatement.setString(1, work_name);
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    topList.put(resultSet.getInt("user_id"), resultSet.getInt("level"));
                }
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
        return topList;
    }

    public static THashMap<Integer, LevelPlayer> getOnlinePlayers() {
        return onlinePlayers;
    }

    public static void removeOnlinePlayer(int id) {
        onlinePlayers.remove(id);
    }

    public static TaskManager getTaskManager() {
        return taskManager;
    }

    public static LevelManager getLevelManager() {
        return levelManager;
    }

    public void getReceibedStepOnTileEvent(UserTakeStepEvent event) {

        try {
            LevelPlayer player = LevelPlayer.getLevelPlayer(event.habbo);
            if (player.getPlayerInfo().getHabbo() != null) {
                if (player != null) {

                    for (Task task : player.getTaskActive().values()) {

                        player.setPlayerStats(player.getPlayerInfo().getHabbo().getHabboInfo().getId(), task.getId());

                        if (task.getType().equalsIgnoreCase("id_furni")) {

                            HabboItem item = event.habbo.getHabboInfo().getCurrentRoom()
                                    .getHabboItem(Integer.parseInt(task.getTypeData()));

                            if (item == null)
                                return;

                            this.setCasillasItem(item);

                            int evento_X = (int) event.toLocation.x;
                            int evento_Y = (int) event.toLocation.y;

                            if (itemListX.get(evento_X) != null && itemListY.get(evento_Y) != null) {
                                TasksWorks.insertProgressTask(player, task);
                            } else {
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
        }

    }

    public void getReceibedToggleEvent(FurnitureToggleEvent event) {
        try {
            LevelPlayer player = LevelPlayer.getLevelPlayer(event.habbo);
            if (player.getPlayerInfo().getHabbo() != null) {
                if (player != null) {

                    Habbo habbo = player.getPlayerInfo().getHabbo();
                    Room room = habbo.getHabboInfo().getCurrentRoom();

                    for (Task task : player.getTaskActive().values()) {
                        player.setPlayerStats(player.getPlayerInfo().getHabbo().getHabboInfo().getId(), task.getId());
                        if (task.getType().equalsIgnoreCase("item_name")) {
                            if (event.furniture.getBaseItem().getName().equalsIgnoreCase(task.getTypeData())) {
                                if (RoomLayout.tilesAdjecent(
                                        room.getLayout().getTile(event.furniture.getX(), event.furniture.getY()),
                                        habbo.getRoomUnit().getCurrentLocation())) {
                                    TasksWorks.insertProgressTask(player, task);
                                }
                            }
                        } else if (task.getType().equalsIgnoreCase("interaction_name")) {
                            if (event.furniture.getBaseItem().getInteractionType().getName()
                                    .equalsIgnoreCase(task.getTypeData())) {
                                if (RoomLayout.tilesAdjecent(
                                        room.getLayout().getTile(event.furniture.getX(), event.furniture.getY()),
                                        habbo.getRoomUnit().getCurrentLocation())) {
                                    TasksWorks.insertProgressTask(player, task);
                                }
                            }
                        }
                    }
                }

            }
        } catch (Exception e) {
        }
    }
}
