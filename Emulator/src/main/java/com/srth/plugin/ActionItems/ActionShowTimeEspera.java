package com.srth.plugin.ActionItems;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

public class ActionShowTimeEspera implements ICycleable {

    private int default_sumatoria = 0;
    private int roomUnitId = -1;
    private String msg;

    public ActionShowTimeEspera(Habbo habbo, int number, String msg) throws SQLException {

        Room room = habbo.getHabboInfo().getCurrentRoom();
        default_sumatoria = number;
        this.msg = msg;

        if (habbo != null && room != null) {

            HabboClient habboClient = HabboClientManager.getHabboClient(habbo);
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (habboClient != null && habboRp != null) {
                if (habboClient.getHabbo().getHabboInfo().getRiding() == null)
                    this.roomUnitId = habbo.getRoomUnit().getId();
                this.cycle(room);
            }

        }
    }

    @Override
    public void cycle(Room room) {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // Esto se ejecuta en segundo plano una única vez
                while (true) {
                    try {
                        // En él, hacemos que el hilo duerma
                        Thread.sleep(1000);
                        if (roomUnitId != -1) {
                            Habbo habbo = room.getHabboByRoomUnitId(roomUnitId);
                            if (habbo != null) {

                                HabboClient client = HabboClientManager.getHabboClient(habbo);

                                int resta_time = default_sumatoria--;
                                int cuenta_total = resta_time;
                                int seconds = cuenta_total;
                                int day = (int) TimeUnit.SECONDS.toDays(seconds);
                                long hours = TimeUnit.SECONDS.toHours(seconds) - (day * 24);
                                long minute = TimeUnit.SECONDS.toMinutes(seconds)
                                        - (TimeUnit.SECONDS.toHours(seconds) * 60);
                                long second = TimeUnit.SECONDS.toSeconds(seconds)
                                        - (TimeUnit.SECONDS.toMinutes(seconds) * 60);

                                String data = "" +
                                        (day != 0 ? day +
                                                (day > 1 ? " dias, " : " dia, ") : "")
                                        +
                                        (hours != 0 ? hours +
                                                (hours > 1 ? " horas, " : " hora, ") : "")
                                        +
                                        (minute != 0 ? minute +
                                                (minute > 1 ? " minutos, " : " minutos, ") : "")
                                        + (second != 0 ? second +
                                                (second > 1 ? " segundos. " : " segundo. ") : "");
                                String mensaje_total = msg.replace("%total_time%", data);

                                JsonArray array = new JsonArray();
                                JsonObject jsonObject;
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("msg", mensaje_total);
                                array.add(jsonObject);

                                try {
                                    client.send(
                                            new MessageManager("MessageManager", true, Outcoming.TimeEsperaUse, array));
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }

                                if (resta_time <= 0) {
                                    try {
                                        client.send(new MessageManager("MessageManager", false, Outcoming.TimeEsperaUse,
                                                null));
                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                    }

                                    resetTime();
                                }
                            }
                        } else {
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        };

        Thread hilo = new Thread(runnable);
        hilo.start();
    }

    private void resetTime() {
        this.roomUnitId = -1;
    }

}
