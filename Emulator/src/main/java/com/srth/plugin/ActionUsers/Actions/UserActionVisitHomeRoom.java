package com.srth.plugin.ActionUsers.Actions;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Mapa.Actions.GoToRoomTaxi;
import com.srth.plugin.Rooms.Mapa.Taxi.ControllerRecorridosTaxi;
import com.srth.plugin.Rooms.Mapa.Taxi.RecorridoTaxi;
import com.srth.plugin.habbo.HabboRp;

public class UserActionVisitHomeRoom {

    public UserActionVisitHomeRoom(GameClient client, Habbo habbo) throws SQLException {

        Room room = client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            client.getHabbo()
                    .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());
        if (habboRp.getJobActive() == null
                || habboRp.getJobActive() != null && !habboRp.getJobActive().getName().equalsIgnoreCase("aseo")) {
            client.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_aseo_job_no_active"),
                    RoomChatMessageBubbles.NORMAL);
            return;
        }

        HabboRp habboRpOther = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        int room_id = habboRpOther.checkSettings("global.home.room.rp.id", 0);

        Emulator.getThreading().run(() -> {
            // GoToRoomTaxi goToRoomTaxi = new GoToRoomTaxi();
            // goToRoomTaxi.setDefaultSumatoria(5);
            // goToRoomTaxi.goTo(client.getHabbo(), room_id);

            ControllerRecorridosTaxi controllerRecorridosTaxi = RoleplayWebsocketsRoomsInfo
                    .getControllerRecorridosTaxi();
            RecorridoTaxi recorrido = controllerRecorridosTaxi
                    .getRecorridoTaxi(habbo);
            if (recorrido == null) {
                habboRp.allowMove(true);
                controllerRecorridosTaxi.setRecorridoTaxi(habbo,
                        room_id);
            }

        }, 500);
    }

}
