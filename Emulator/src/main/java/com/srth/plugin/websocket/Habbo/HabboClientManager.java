package com.srth.plugin.websocket.Habbo;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class HabboClientManager {

    public static List<HabboClient> clients = new CopyOnWriteArrayList<>();

    public void addClient(Channel channel, Habbo habbo) {
        synchronized (HabboClientManager.class) {
            clients.removeIf(client -> sameHabbo(client.getHabbo(), habbo)
                    || client.getChannel().id().equals(channel.id()));
            clients.add(new HabboClient(habbo, channel));
        }
    }

    public static synchronized void killClient(ChannelId channelid) {
        clients.removeIf(client -> client.getChannel().id().equals(channelid));
    }

    public static synchronized HabboClient getHabboClient(Habbo habbo) {
        for (HabboClient client : clients) {
            if (sameHabbo(client.getHabbo(), habbo)) {
                return client;
            }
        }
        return null;
    }

    public static synchronized HabboClient getHabboClient(Channel channel) {
        for (HabboClient client : clients) {
            if (client.getChannel().equals(channel)) {
                return client;
            }
        }
        return null;
    }

    public static void sendBroadcast(OutgoingMessage message) {
        for (HabboClient client : clients) {
            client.send(message);
        }
    }

    private static boolean sameHabbo(Habbo first, Habbo second) {
        return first == second
                || first != null
                        && second != null
                        && Objects.equals(
                                first.getHabboInfo().getId(),
                                second.getHabboInfo().getId());
    }
}
