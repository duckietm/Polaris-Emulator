package com.srth.plugin.events.habboEvents.tasksWorks;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.sql.SQLException;

import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.users.UserCommandEvent;
import com.srth.plugin.habbo.Jobs.TasksWorks;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.habbo.Jobs.Tasks.Task;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;

public class UserCommandEvento implements EventListener {
    
    @EventHandler
    public static void UserCommandEvento(UserCommandEvent event) throws SQLException {

        // Logger LOGGER = LoggerFactory.getLogger(Emulator.class);
        HabboClient client = HabboClientManager.getHabboClient(event.habbo);
        LevelPlayer player = LevelPlayer.getLevelPlayer(event.habbo);

        if(client != null) {
            if(player != null){

                for(Task task : player.getTaskActive().values()){
                    player.setPlayerStats(player.getPlayerInfo().getHabbo().getHabboInfo().getId(), task.getId());
                    if(task.getType().equalsIgnoreCase("command")){
                        if(event.args[0].equalsIgnoreCase(task.getTypeData()) && event.succes == true){
                            TasksWorks.insertProgressTask(player, task);
                        }
                    }
                }

            }
        }
        
    }
}
