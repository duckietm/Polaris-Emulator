package com.srth.plugin.websocket.communication.outgoing.messages.outworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.habbo.Jobs.Tasks.Task;
import com.srth.plugin.habbo.Jobs.Tasks.TaskManager;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class OutShowVentanaTaskActive extends OutgoingMessage {
    public OutShowVentanaTaskActive(String header, boolean show, int mostrar, LevelPlayer player) throws SQLException {
        super("ShowVentanaTaskActive");

        // Actualizar las tareas
        new TaskManager().getTasksForPlayer(player);
        new TaskManager().getTaskActive(player);

        this.data.addProperty("show_button", show);
        this.data.addProperty("mostrar", mostrar);
        this.data.addProperty("ver", mostrar);

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String url_images = RoleplayWebsocketsRoomsInfo.getConfig()
                .getValue("global_url.hotel.notifications")
                .replace("%url.hotel.base%", url_hotel_base) + "/RoleplayWebsocketsRoomsInfo/";

        if (player != null) {

            JsonArray TarrayTask = new JsonArray();
            JsonObject tasks;

            for (Task task : player.getTasks().values()) {
                tasks = new JsonObject();
                tasks.addProperty("id", task.getId());

                if (player.getPlayerInfo().getTaskActive().contains(task.getId())) {

                    player.setPlayerStats(player.getPlayerInfo().getHabbo().getHabboInfo().getId(), task.getId());

                    tasks.addProperty("active", "1");
                    tasks.addProperty("id", task.getId());
                    tasks.addProperty("process", player.getPlayerStats().getProcess());
                    tasks.addProperty("max_process", task.getProcessRequired());

                    tasks.addProperty("title", task.getName());
                    tasks.addProperty("description", task.getDescription());
                    tasks.addProperty("image", url_images + "tasks/" + task.getImage());
                    tasks.addProperty("xp", task.getXP());

                    int player_process = player.getPlayerStats().getProcess();
                    int task_max_proccess = task.getProcessRequired();
                    int porcentaje = player_process / task_max_proccess * 100;
                    tasks.addProperty("porcentaje", porcentaje + "%");

                    TarrayTask.add(tasks);

                }
            }

            // this.data.add("button", jsonObject);
            this.data.add("button", TarrayTask);
        }

        // this.data.add("tasks", tasksobj);
        this.dataArray.add(this.data);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}
