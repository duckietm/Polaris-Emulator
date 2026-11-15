package com.srth.plugin.websocket.communication.incoming.messages;

import com.google.gson.JsonObject;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;
import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.nio.charset.StandardCharsets;

public class PingEvent extends IncomingMessage {

    public PingEvent(String header, THashMap<String,Object> data, Channel client) {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() {

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("header","PongEvent");
        jsonObject.addProperty("data","Pong!");

        this.client.writeAndFlush(new BinaryWebSocketFrame(this.client.alloc().buffer().writeBytes(jsonObject.toString().getBytes(StandardCharsets.UTF_8))));
    }
}
