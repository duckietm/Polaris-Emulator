package com.srth.plugin.websocket.communication.incoming;

import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

import java.sql.SQLException;

public abstract class IncomingMessage {
    public final String header;
    public final THashMap<String, Object> data;
    public final Channel client;

    public IncomingMessage(String header, THashMap<String, Object> data, Channel client) {
        this.header = header;
        this.data = data;
        this.client = client;
    }

    public abstract void execute() throws SQLException;

}
