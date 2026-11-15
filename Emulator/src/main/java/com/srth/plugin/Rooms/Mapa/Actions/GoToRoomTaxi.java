package com.srth.plugin.Rooms.Mapa.Actions;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowMapaRoleplay;

import gnu.trove.map.hash.THashMap;

public class GoToRoomTaxi implements ICycleable {

    private THashMap<Integer, Room> user_visited = new THashMap<Integer, Room>();

    private int default_sumatoria = 3;
    private int timesumatoria = default_sumatoria;
    private int startTime = 0;
    private int roomUnitId = -1;
    private int roomUnitY = -1;
    private int roomUnitX = -1;
    private int room_id = 0;

    public void setDefaultSumatoria(int time) {
        this.default_sumatoria = time;
        this.timesumatoria = time;
    }

    public GoToRoomTaxi() {
    }

    public void goTo(Habbo habbo, int room_id) {

        try {
            if (user_visited.get(habbo.getHabboInfo().getId()) != null) {
                user_visited.remove(habbo.getHabboInfo().getId());
            }

            Room room = habbo.getHabboInfo().getCurrentRoom();
            this.room_id = room_id;

            if (habbo != null && room != null) {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                HabboClient habboClient = habboRp.getHabboClient();

                if (habboClient != null && habboRp != null) {

                    if (habboRp.getProgressPendiente() == true) {
                        return;
                    }

                    if (habboClient.getHabbo().getHabboInfo().getRiding() == null) {

                        habboClient.send(new ShowMapaRoleplay("ShowMapaRoleplay", false, null, null));

                        habboClient.getHabbo().getRoomUnit().cmdLay = false;
                        habboClient.getHabbo().getHabboInfo().getCurrentRoom().updateHabbo(habboClient.getHabbo());
                        habboClient.getHabbo().getRoomUnit().cmdSit = false;
                        habboClient.getHabbo().getRoomUnit()
                                .setBodyRotation(RoomUserRotation.values()[habboClient.getHabbo().getRoomUnit()
                                        .getBodyRotation().getValue()
                                        - habboClient.getHabbo().getRoomUnit().getBodyRotation().getValue() % 2]);

                        habboRp.getVidaHambreDormir().showTimeProcess(
                                "RoleplayWebsocketsRoomsInfo/actions/taxi.png?v2",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.usetaximap.process"),
                                timesumatoria, true);

                        // Por defecto 634 // Personalizado 750
                        int enable_taxi = RoleplayWebsocketsRoomsInfo.getConfig().getInt("enable_taxi_id");
                        room.giveEffect(habbo, Integer.valueOf(enable_taxi), -1);

                        this.roomUnitId = habbo.getRoomUnit().getId();
                        this.roomUnitX = habbo.getRoomUnit().getX();
                        this.roomUnitY = habbo.getRoomUnit().getY();
                        this.startTime = Emulator.getIntUnixTimestamp();
                        this.cycle(room);
                    }
                }
            }
        } catch (Exception e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER(e + "");
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

                                if (roomUnitY != habbo.getRoomUnit().getY()
                                        || roomUnitX != habbo.getRoomUnit().getX()) {
                                    try {
                                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                                .getHabboRp(habbo);

                                        habboRp.getVidaHambreDormir().ocultarTimeProcess();
                                        habboRp.setProgressPendiente(false);

                                        room.giveEffect(habbo, Integer.valueOf(0), -1);

                                        habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts()
                                                .getValue("global_action_canceled"));
                                    } catch (SQLException e) {
                                    }

                                    resetTime();
                                    return;
                                }

                                int timestamp = Emulator.getIntUnixTimestamp();
                                int resta_time = timestamp - startTime;

                                if (resta_time >= timesumatoria) {
                                    try {
                                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                                .getHabboRp(habbo);

                                        Room room_visit = Emulator.getGameEnvironment().getRoomManager()
                                                .loadRoom(room_id);
                                        String MSG1 = RoleplayWebsocketsRoomsInfo.getTexts()
                                                .getValue("window_mapa.msg_visit_exitosa")
                                                .replace("%room_name%", room_visit.getName());
                                        habbo.talk(MSG1, RoomChatMessageBubbles.NORMAL);
                                        user_visited.put(client.getHabbo().getHabboInfo().getId(), room_visit);

                                        // Insertar tarea
                                        habboRp.getTareas().insertProgress("UsarTaxi");

                                        client.getHabbo().goToRoom(room_id);

                                        habboRp.setProgressPendiente(false);
                                        habboRp.getVidaHambreDormir().ocultarTimeProcess();
                                        resetTime();

                                        room.giveEffect(habbo, Integer.valueOf(0), -1);

                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                    }

                                }
                            }
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
        int timestamp = Emulator.getIntUnixTimestamp();
        this.roomUnitId = -1;
        this.startTime = timestamp;
        this.timesumatoria = default_sumatoria;
        this.roomUnitX = -1;
        this.roomUnitY = -1;
        this.room_id = 0;
    }

    public Room getHabboRoomVisitar(Habbo habbo) {
        return user_visited.get(habbo.getHabboInfo().getId());
    }

}
