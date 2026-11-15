package com.srth.plugin.websocket.Habbo;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.nio.charset.StandardCharsets;

public class HabboClient {

    private final Habbo habbo;
    private final Channel channel;

    public HabboClient(Habbo habbo, Channel channel){
        this.channel = channel;
        this.habbo = habbo;

    }

    public void send(OutgoingMessage message){
        this.channel.writeAndFlush(new BinaryWebSocketFrame(this.channel.alloc().buffer().writeBytes(message.execute().getBytes(StandardCharsets.UTF_8))));
    }
    
    public void send(String test){
        this.channel.writeAndFlush(new BinaryWebSocketFrame(this.channel.alloc().buffer().writeBytes(test.getBytes(StandardCharsets.UTF_8))));
    }

    public Habbo getHabbo() {
        return habbo;
    }

    public Channel getChannel() {
        return channel;
    }

}
