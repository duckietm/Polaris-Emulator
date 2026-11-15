package com.srth.plugin.websocket.communication.incoming.messages;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Mapa.Actions.GoToRoomTaxi;
import com.srth.plugin.Rooms.Mapa.Taxi.ControllerRecorridosTaxi;
import com.srth.plugin.Rooms.Mapa.Taxi.RecorridoTaxi;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;
import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

import java.sql.SQLException;

public class GoToRoomEvent extends IncomingMessage {
    public GoToRoomEvent(String header, THashMap<String, Object> data, Channel client) throws SQLException {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() throws SQLException {

        HabboClient clientws = HabboClientManager.getHabboClient(this.client);
        int room_id = (int) ((double) data.get("room_id"));

        if (clientws != null) {
            // GoToRoomTaxi goToRoomTaxi = new GoToRoomTaxi();
            // goToRoomTaxi.setDefaultSumatoria(10);
            // goToRoomTaxi.goTo(clientws.getHabbo(), room_id);

            ControllerRecorridosTaxi controllerRecorridosTaxi = RoleplayWebsocketsRoomsInfo
                    .getControllerRecorridosTaxi();
            RecorridoTaxi recorrido = controllerRecorridosTaxi
                    .getRecorridoTaxi(clientws.getHabbo());
            if (recorrido == null) {
                controllerRecorridosTaxi.setRecorridoTaxi(clientws.getHabbo(),
                        room_id);
            }
        }
    }
}
