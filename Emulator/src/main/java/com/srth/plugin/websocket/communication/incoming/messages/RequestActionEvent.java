package com.srth.plugin.websocket.communication.incoming.messages;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Mapa.MapaManager;
import com.srth.plugin.Tutorial.partes_empleos.PartesEmpleosTutorial;
import com.srth.plugin.habbo.Frank.Frank.FrankManager;
import com.srth.plugin.habbo.Shooter.ShooterManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;

import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

import java.sql.SQLException;

public class RequestActionEvent extends IncomingMessage {
    public RequestActionEvent(String header, THashMap<String, Object> data, Channel client) throws SQLException {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() throws SQLException {

        HabboClient wsclient = HabboClientManager.getHabboClient(this.client);
        if (wsclient == null) {
            return;
        }

        RoleplayWebsocketsRoomsInfo.getLevelManagerRP().IncomingListActions(data, wsclient);
        RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().IncomingListActions(data, wsclient);

        new FrankManager().IncomingListActions(data, wsclient);
        new MapaManager().IncomingListActions(data, wsclient);

        RoleplayWebsocketsRoomsInfo.getRetosTutorialManager().IncomingListActions(data, wsclient);
        RoleplayWebsocketsRoomsInfo.getTareasManager().IncomingListActions(data, wsclient);

        new ShooterManager().IncomingListActions(data, wsclient);

        RoleplayWebsocketsRoomsInfo.getCraffteoManager().incomingEvents(data, wsclient);

    }

}
