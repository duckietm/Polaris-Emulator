package com.eu.habbo.habbohotel.gameclients;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.channel.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class GameClientManager {

    private final ConcurrentMap<ChannelId, GameClient> clients;

    public GameClientManager() {
        this.clients = new ConcurrentHashMap<>();
    }


    public ConcurrentMap<ChannelId, GameClient> getSessions() {
        return this.clients;
    }


    public boolean addClient(ChannelHandlerContext ctx) {
        GameClient client = new GameClient(ctx.channel());
        ctx.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                GameClientManager.this.disposeClient(ctx.channel());
            }
        });

        ctx.channel().attr(GameServerAttributes.CLIENT).set(client);
        ctx.fireChannelRegistered();

        return this.clients.putIfAbsent(ctx.channel().id(), client) == null;
    }


    public void disposeClient(GameClient client) {
        if (client == null) {
            return;
        }

        this.disposeClient(client.getChannel(), true);
    }

    public void forceDisposeClient(GameClient client) {
        if (client == null) {
            return;
        }

        this.disposeClient(client.getChannel(), false);
    }

    private void disposeClient(Channel channel) {
        this.disposeClient(channel, true);
    }

    private void disposeClient(Channel channel, boolean allowSessionResume) {
        if (channel == null) {
            return;
        }

        GameClient client = channel.attr(GameServerAttributes.CLIENT).get();

        if (client != null) {
            client.dispose(allowSessionResume);
        }
        channel.deregister();
        channel.attr(GameServerAttributes.CLIENT).set(null);
        channel.closeFuture();
        channel.close();
        this.clients.remove(channel.id());
    }


    public boolean containsHabbo(Integer id) {
        if (!this.clients.isEmpty()) {
            for (GameClient client : this.clients.values()) {
                if (client.getHabbo() != null) {
                    if (client.getHabbo().getHabboInfo() != null) {
                        if (client.getHabbo().getHabboInfo().getId() == id)
                            return true;
                    }
                }
            }
        }
        return false;
    }


    public Habbo getHabbo(int id) {
        for (GameClient client : this.clients.values()) {
            if (client.getHabbo() == null)
                continue;

            if (client.getHabbo().getHabboInfo().getId() == id)
                return client.getHabbo();
        }

        return null;
    }


    public Habbo getHabbo(String username) {
        for (GameClient client : this.clients.values()) {
            if (client.getHabbo() == null)
                continue;

            if (client.getHabbo().getHabboInfo().getUsername().equalsIgnoreCase(username))
                return client.getHabbo();
        }

        return null;
    }


    public List<Habbo> getHabbosWithIP(String ip) {
        List<Habbo> habbos = new ArrayList<>();

        for (GameClient client : this.clients.values()) {
            if (client.getHabbo() != null && client.getHabbo().getHabboInfo() != null) {
                if (client.getHabbo().getHabboInfo().getIpLogin().equalsIgnoreCase(ip)) {
                    habbos.add(client.getHabbo());
                }
            }
        }

        return habbos;
    }


    /**
     * Find an existing GameClient that authenticated with the given SSO ticket.
     * Used to detect reconnections where the old connection hasn't been closed yet.
     */
    public GameClient findClientBySsoTicket(String ssoTicket) {
        if (ssoTicket == null || ssoTicket.isEmpty()) return null;

        for (GameClient client : this.clients.values()) {
            if (ssoTicket.equals(client.getSsoTicket()) && client.getHabbo() != null) {
                return client;
            }
        }
        return null;
    }


    public List<Habbo> getHabbosWithMachineId(String machineId) {
        List<Habbo> habbos = new ArrayList<>();

        for (GameClient client : this.clients.values()) {
            if (client.getHabbo() != null && client.getHabbo().getHabboInfo() != null && client.getMachineId().equalsIgnoreCase(machineId)) {
                habbos.add(client.getHabbo());
            }
        }

        return habbos;
    }


    public void sendBroadcastResponse(MessageComposer composer) {
        this.sendBroadcastResponse(composer.compose());
    }


    public void sendBroadcastResponse(ServerMessage message) {
        for (GameClient client : this.clients.values()) {
            client.sendResponse(message);
        }
    }


    public void sendBroadcastResponse(ServerMessage message, GameClient exclude) {
        for (GameClient client : this.clients.values()) {
            if (client.equals(exclude))
                continue;

            client.sendResponse(message);
        }
    }


    public void sendBroadcastResponse(ServerMessage message, String minPermission, GameClient exclude) {
        for (GameClient client : this.clients.values()) {
            if (client.equals(exclude))
                continue;

            if (client.getHabbo() != null) {
                if (client.getHabbo().hasPermission(minPermission)) {
                    client.sendResponse(message);
                }
            }
        }
    }

    public void CFKeepAlive() {
        Emulator.getThreading().run(() -> {
            for (GameClient client : this.clients.values()) {
                if (client != null && client.getChannel().isActive()) {
                    client.sendKeepAlive();
                }
            }
            CFKeepAlive();
        }, 30000);
    }
}
