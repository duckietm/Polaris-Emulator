package com.srth.plugin.websocket.communication.incoming.messages;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionItems.ItemActionManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;

import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

import java.sql.SQLException;

public class RequestItemActionsEvent extends IncomingMessage {
    public RequestItemActionsEvent(String header, THashMap<String, Object> data, Channel client) throws SQLException {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() throws SQLException {

        HabboClient wsclient = HabboClientManager.getHabboClient(this.client);
        if (wsclient == null) {
            return;
        }

        
        RoleplayWebsocketsRoomsInfo.getRetosTutorialManager().getIncomingActions(wsclient.getHabbo().getClient(), data);
        RoleplayWebsocketsRoomsInfo.getItemActionManager().getIncomingActions(wsclient.getHabbo().getClient(),
                data);
        ItemActionManager.getCajeroAbierto(wsclient.getHabbo()).getIncomingActions(wsclient.getHabbo().getClient(),
                data);

    }
}
