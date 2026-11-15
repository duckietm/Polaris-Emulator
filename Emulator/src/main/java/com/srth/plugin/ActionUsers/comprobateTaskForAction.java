package com.srth.plugin.ActionUsers;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.habbo.Jobs.TasksWorks;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.habbo.Jobs.Tasks.Task;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;

public class comprobateTaskForAction {

    public comprobateTaskForAction(String name_type, Habbo habbo) throws SQLException {

        // Logger LOGGER = LoggerFactory.getLogger(Emulator.class);
        HabboClient client = HabboClientManager.getHabboClient(habbo);
        LevelPlayer player;
        try {
            player = LevelPlayer.getLevelPlayer(habbo);

            if (client != null) {
                if (player != null) {
                    for (Task task : player.getTaskActive().values()) {
                        player.setPlayerStats(player.getPlayerInfo().getHabbo().getHabboInfo().getId(), task.getId());
                        if (name_type.equalsIgnoreCase(task.getActionName())) {
                            TasksWorks.insertProgressTask(player, task);
                        }
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
