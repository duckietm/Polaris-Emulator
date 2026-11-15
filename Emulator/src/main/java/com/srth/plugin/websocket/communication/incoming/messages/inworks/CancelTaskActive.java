package com.srth.plugin.websocket.communication.incoming.messages.inworks;

import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.OutCancelTaskActive;

import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

import java.sql.SQLException;

public class CancelTaskActive extends IncomingMessage {
    public CancelTaskActive(String header, THashMap<String, Object> data, Channel client) throws SQLException {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() throws SQLException {

        HabboClient client = HabboClientManager.getHabboClient(this.client);
        if(client != null)
           new OutCancelTaskActive(client.getHabbo());
    }
}
