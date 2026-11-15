package com.srth.plugin.habbo.Jobs;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.habbo.Jobs.Tasks.Task;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.OutCancelTaskActive;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.OutShowUserActions;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.OutShowVentanaTaskActive;

import gnu.trove.map.hash.THashMap;

public class ManagerButtonsRight {

    public ManagerButtonsRight() throws SQLException {
    }

    public void generateTask(Habbo habbo, int task_id) throws SQLException {
        HabboClient client = HabboClientManager.getHabboClient(habbo);
        LevelPlayer player = LevelPlayer.getLevelPlayer(habbo);
        if (player == null) {
            LevelPlayer.createLevelPlayer(habbo);
            WorkSystemLevelManager.addOnlinePlayer(LevelPlayer.getLevelPlayer(habbo));
        } else {
            WorkSystemLevelManager.addOnlinePlayer(player);
        }
        if (player != null) {
            if (player.getAmountTaskActive() == 0) {
                player.generateTask(task_id);
                if (player.getAmountTaskActive() >= 1) {
                    client.send(new OutShowVentanaTaskActive("ShowVentanaTaskActive", true, 1, player));
                }
            }
        }
    }

    public void generateRandomTask(Habbo habbo) throws SQLException {

        HabboClient client = HabboClientManager.getHabboClient(habbo);

        LevelPlayer player = LevelPlayer.getLevelPlayer(habbo);
        if (player == null) {
            LevelPlayer.createLevelPlayer(habbo);
            WorkSystemLevelManager.addOnlinePlayer(LevelPlayer.getLevelPlayer(habbo));
        } else {
            WorkSystemLevelManager.addOnlinePlayer(player);
        }

        if (player != null) {

            if (player.getAmountTaskActive() == 0) {

                player.generateRandomTask();

                if (player.getAmountTaskActive() >= 1) {
                    client.send(new OutShowVentanaTaskActive("ShowVentanaTaskActive", true, 1, player));
                }

            }
        }
    }

    public void ViewButtons(Habbo habbo) {
        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            int home_room_id = habboRp.checkSettings("global.home.room.rp.id", 0);

            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            String url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                    .replace("%url.hotel.base%", url_hotel_base) + "RoleplayWebsocketsRoomsInfo/";

            JsonArray Button = new JsonArray();
            JsonObject jsonObject;

            if (habboRp.getJobActive() != null) {
                String name = habboRp.getJobActive().getName();
                int job_room_id = habboRp.getJobActive().getRoomId();

                LevelPlayer player = LevelPlayer.getLevelPlayer(habbo);
                if (player != null) {

                    TasksWorks.comprobateTimeAndUpdateAllTask();

                    THashMap<Integer, Task> totaltaskactive = WorkSystemLevelManager.getTaskManager()
                            .getTaskActive(player);
                    int total = totaltaskactive.keySet().size();
                    if (total >= 1) {
                        habboRp.getHabboClient()
                                .send(new OutShowVentanaTaskActive("ShowVentanaTaskActive", true, 1, player));
                    }

                    jsonObject = new JsonObject();
                    jsonObject.addProperty("process", 1);
                    if (habboRp.getJobActive() != null) {
                        jsonObject.addProperty("active", 1);
                    } else {
                        jsonObject.addProperty("active", 0);
                    }

                    jsonObject.addProperty("work_name", name);
                    jsonObject.addProperty("work_room_id", job_room_id);
                    jsonObject.addProperty("home_room_id", home_room_id);
                    jsonObject.addProperty("title", "Completar tareas del empleo");
                    jsonObject.addProperty("image", url_images + "jobs/job_" + name + ".png");
                    Button.add(jsonObject);
                }
            }

            jsonObject = new JsonObject();
            jsonObject.addProperty("process", 3);
            jsonObject.addProperty("process_name", "ShowMapaRoleplay");
            jsonObject.addProperty("process_data", "1");
            jsonObject.addProperty("active", 1);
            jsonObject.addProperty("work_room_id", 0);
            jsonObject.addProperty("home_room_id", home_room_id);
            jsonObject.addProperty("work_name", "");
            jsonObject.addProperty("title", "Ver el mapa");
            jsonObject.addProperty("image", url_images + "actions/mapa1.png");
            Button.add(jsonObject);

            jsonObject = new JsonObject();
            jsonObject.addProperty("process", 2);
            jsonObject.addProperty("active", 1);
            jsonObject.addProperty("work_room_id", 0);
            jsonObject.addProperty("home_room_id", home_room_id);
            jsonObject.addProperty("work_name", "");
            jsonObject.addProperty("title", "Ir a mi casa");
            jsonObject.addProperty("image", url_images + "jobs/casa.png");
            Button.add(jsonObject);

            habboRp.getHabboClient().send(new OutShowUserActions("ShowUserActions", true, Button));
        } catch (Exception e) {
        }

    }

    public void ocultWindowWork(Habbo habbo) throws SQLException {
        new OutCancelTaskActive(habbo);
    }

}
