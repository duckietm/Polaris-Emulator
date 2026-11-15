package com.srth.plugin.Consola;

import java.sql.SQLException;
import java.util.Map;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.JobEmpleado;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

public class ConsolaManager {

    public ConsolaManager() {
    }

    public void showConsola(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(
                        new MessageManager("MessageManager", true, Outcoming.ConsolaTimeLogs, this.getData(habbo)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ocultConsola(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp.getJobActive() == null)
                return;

            if (habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(
                        new MessageManager("MessageManager", false, Outcoming.ConsolaTimeLogs, null));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JsonArray getData(Habbo habbo) throws SQLException {

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String urlNotifications = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                .replace("%url.hotel.base%", url_hotel_base);

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        JsonArray TarrayTask = new JsonArray();
        JsonObject tasks = new JsonObject();

        tasks.addProperty("id", habbo.getHabboInfo().getId());

        TarrayTask.add(tasks);
        return TarrayTask;
    }

    public void sendMessageConsola(String html, Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp.getJobActive() == null)
                return;

            if (habboRp.getHabboClient() != null) {

                JsonArray ArrayInfo = new JsonArray();
                JsonObject data = new JsonObject();
                data.addProperty("id", habbo.getHabboInfo().getId());
                data.addProperty("html", html);
                ArrayInfo.add(data);

                habboRp.getHabboClient().send(
                        new MessageManager("MessageManager", true, Outcoming.ConsolaNotification,
                                ArrayInfo));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessageConsolaGlobal(String html) {
        for (Map.Entry<Integer, Habbo> map : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos()
                .entrySet()) {
            Habbo habbo = map.getValue();

            if (habbo == null || !habbo.isOnline())
                continue;

            final Room room = habbo.getHabboInfo().getCurrentRoom();

            // Validamos que esté en una sala y que sea de categoría Roleplay
            if (room != null) {
                int rpCategoryId = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id");

                if (room.getCategory() != rpCategoryId) {
                    continue;
                }

                try {

                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                    JsonArray ArrayInfo = new JsonArray();
                    JsonObject data = new JsonObject();
                    data.addProperty("id", habbo.getHabboInfo().getId());
                    data.addProperty("html", html);
                    ArrayInfo.add(data);

                    habboRp.getHabboClient().send(
                            new MessageManager("MessageManager", true, Outcoming.ConsolaNotification,
                                    ArrayInfo));

                } catch (Exception e) {
                }
            }
        }
    }

}
