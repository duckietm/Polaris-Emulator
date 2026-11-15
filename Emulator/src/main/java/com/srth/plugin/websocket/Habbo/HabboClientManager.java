package com.srth.plugin.websocket.Habbo;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;

import java.util.ArrayList;
import java.util.List;

public class HabboClientManager {

    public static List<HabboClient> clients = new ArrayList<>();
    
    public void addClient(Channel channel, Habbo habbo){
        clients.add(new HabboClient(habbo, channel));
    }

    public static void killClient(ChannelId channelid){
        clients.removeIf((x) -> x.getChannel().id().equals(channelid));
    }

    public static HabboClient getHabboClient(Habbo habbo){
        for(HabboClient client : clients){
            if(client.getHabbo().equals(habbo)){
                return client;
            }
        }
        return null;
    }

    public static HabboClient getHabboClient(Channel channel){
        for(HabboClient client : clients){
            if(client.getChannel().equals(channel)){
                return client;
            }
        }
        return null;
    }

    public static void sendBroadcast(OutgoingMessage message){
        for(HabboClient client: clients){
            client.send(message);
        }
    }
}
