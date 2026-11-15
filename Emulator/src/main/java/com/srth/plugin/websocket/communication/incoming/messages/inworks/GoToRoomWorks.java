package com.srth.plugin.websocket.communication.incoming.messages.inworks;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.rooms.Room;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Mapa.Actions.GoToRoomTaxi;
import com.srth.plugin.Rooms.Mapa.Taxi.ControllerRecorridosTaxi;
import com.srth.plugin.Rooms.Mapa.Taxi.RecorridoTaxi;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDouble;

import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

public class GoToRoomWorks extends IncomingMessage {
    public GoToRoomWorks(String header, THashMap<String, Object> data, Channel client) throws SQLException {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() throws SQLException {

        HabboClient client = HabboClientManager.getHabboClient(this.client);

        if (client != null)
            client.send(new MessageManagerDouble("MessageManager", false, Outcoming.OpenMenuJobs, null, null));

        Room room = client.getHabbo().getHabboInfo().getCurrentRoom();
        int goto_room_id = (int) ((double) data.get("room_id"));

        if (room.getId() == goto_room_id) {

            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert("RoleplayWebsocketsRoomsInfo/tutorial/casa.png",
                    "global_alert.room.actual", client.getHabbo().getClient());

            // Generar efecto
            client.getHabbo().getRoomUnit().setCanWalk(true);
            room.giveEffect(client.getHabbo(),
                    Integer.valueOf(0),
                    -1);

            return;

        } else {

            // GoToRoomTaxi goToRoomTaxi = new GoToRoomTaxi();
            // goToRoomTaxi.setDefaultSumatoria(7);
            // goToRoomTaxi.goTo(client.getHabbo(), goto_room_id);

            ControllerRecorridosTaxi controllerRecorridosTaxi = RoleplayWebsocketsRoomsInfo
                    .getControllerRecorridosTaxi();
            RecorridoTaxi recorrido = controllerRecorridosTaxi
                    .getRecorridoTaxi(client.getHabbo());
            if (recorrido == null) {
                controllerRecorridosTaxi.setRecorridoTaxi(client.getHabbo(),
                        goto_room_id);
            }
        }
    }
}
