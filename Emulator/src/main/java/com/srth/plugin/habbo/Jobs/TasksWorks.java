package com.srth.plugin.habbo.Jobs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.habbo.Jobs.Tasks.Task;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.OutShowVentanaTaskActive;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

public class TasksWorks {

    public static void comprobateTimeAndUpdateAllTask(){

        int time_current = RoleplayWebsocketsRoomsInfo.getConfig().getInt("tasks_times_interval_current");
        int dais_amount = RoleplayWebsocketsRoomsInfo.getConfig().getInt("tasks_times_interval_dais.amount");
        int dais_int_seconds = 86400;
        int dais_operation = dais_int_seconds*dais_amount;

        if(Emulator.getIntUnixTimestamp() >= time_current+dais_operation){

            RoleplayWebsocketsRoomsInfo.getConfig().saveToDatabase(Emulator.getIntUnixTimestamp()+"","tasks_times_interval_current");

            try(Connection conn = Emulator.getDatabase().getDataSource().getConnection()){
                try(PreparedStatement statement = conn.prepareStatement("UPDATE rp_jobs_tasks_users_process SET active = ? , process = ?, status = ?")){
                    statement.setInt(1, 0);
                    statement.setInt(2, 0);
                    statement.setString(3, "incomplete");
                    statement.executeUpdate();
                    statement.close();
                }
            } catch (SQLException exception) {
                exception.printStackTrace();
            }

        }

    }

    public static void insertProgressTask(LevelPlayer player, Task task) throws SQLException{

        if(player == null || task == null){
            return;
        }

        GameClient client = player.getPlayerInfo().getHabbo().getClient();
        HabboClient Client = HabboClientManager.getHabboClient(player.getPlayerInfo().getHabbo());

        if(Client != null)

            if(player.getPlayerStats().getProcess() >= task.getProcessRequired()-1){

                player.resetTasksActive();
                player.getPlayerInfo().addTaskDone(task.getId());
                player.getPlayerInfo().addXP(task.getXP());
                client.getHabbo().alert("Completaste una tarea de tu empleo, recibiste: "+task.getXP()+" XP.");
                
                // Ocultar ventana activa
                Client.send(new OutShowVentanaTaskActive("ShowVentanaTaskActive", false, 0, player));

                HabboClient habboClient = HabboClientManager.getHabboClient(player.getPlayerInfo().getHabbo());
                habboClient.send(new PlaySound("PlaySound", true, "success.mp3", client.getHabbo()));
                
            } else {
                player.getPlayerInfo().addTaskProcess(task.getId(), player.getPlayerStats().getProcess()+1);

                Client.send(new OutShowVentanaTaskActive("ShowVentanaTaskActive", true, 1, player));
            }
    }

    public void CheckAndInsertTaskActive(HabboItem item, Habbo habbo){
        
        try {
            HabboClient client = HabboClientManager.getHabboClient(habbo);
            LevelPlayer player = LevelPlayer.getLevelPlayer(habbo);
            
            if(client != null) {
                if(player != null){
                    if(item != null){

                        for(Task task : player.getTaskActive().values()){

                            player.setPlayerStats(player.getPlayerInfo().getHabbo().getHabboInfo().getId(), task.getId());
                            
                            if(task.getType().equalsIgnoreCase("interaction")){

                                if(item.getBaseItem().getInteractionType().getName().equalsIgnoreCase(task.getTypeData())){
                                    
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
