package com.srth.plugin.websocket.communication.incoming.messages;

import com.srth.plugin.habbo.ConfirmEvents.confirmevents.ConfirmEventsManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;

import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;
import java.sql.SQLException;

public class ReceibedConfirmAlert extends IncomingMessage {
    
    public ReceibedConfirmAlert(String header, THashMap<String, Object> data, Channel client) throws SQLException {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() throws SQLException {

        HabboClient clientws = HabboClientManager.getHabboClient(this.client);

        if(clientws != null){

            new ConfirmEventsManager().IncomingListActions(data, clientws);

        }
    }
}
