package polaris.hotel.gameclients;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.handshake.DisconnectReasonComposer;
import polaris.networking.gameserver.GameServerAttributes;

public class GameClientManager {

    private final ConcurrentMap<ChannelId, GameClient> clients;
    private final ConcurrentMap<Integer, GameClient> authenticatedClients;

    public GameClientManager() {
        this.clients = new ConcurrentHashMap<>();
        this.authenticatedClients = new ConcurrentHashMap<>();
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

    /**
     * Tells the client why its session ends (DisconnectReason), then closes it without session resume.
     * The packet is flushed first so it reaches the client before the close; the client then shows the
     * reason and does not reconnect.
     */
    public void disconnectWithReason(GameClient client, int reason) {
        if (client == null) {
            return;
        }

        Channel channel = client.getChannel();
        if (channel != null && channel.isActive()) {
            client.sendResponse(new DisconnectReasonComposer(reason));
            channel.flush();
        }

        this.forceDisposeClient(client);
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
            if (client.getUser() != null && client.getUser().getUserInfo() != null) {
                this.releaseAuthenticatedSession(client.getUser().getUserInfo().getId(), client);
            }
            client.dispose(allowSessionResume);
        }
        channel.deregister();
        channel.attr(GameServerAttributes.CLIENT).set(null);
        channel.closeFuture();
        channel.close();
        this.clients.remove(channel.id());
    }

    public GameClient claimAuthenticatedSession(int userId, GameClient client) {
        if (userId <= 0 || client == null) return null;
        return this.authenticatedClients.put(userId, client);
    }

    public void releaseAuthenticatedSession(int userId, GameClient client) {
        if (userId <= 0 || client == null) return;
        this.authenticatedClients.remove(userId, client);
    }

    public GameClient getAuthenticatedClient(int userId) {
        if (userId <= 0) return null;
        return this.authenticatedClients.get(userId);
    }

    public int getAuthenticatedSessionCount(int userId) {
        if (userId <= 0) return 0;
        return this.authenticatedClients.containsKey(userId) ? 1 : 0;
    }

    public boolean containsUser(Integer id) {
        if (!this.clients.isEmpty()) {
            for (GameClient client : this.clients.values()) {
                if (client.getUser() != null) {
                    if (client.getUser().getUserInfo() != null) {
                        if (client.getUser().getUserInfo().getId() == id) return true;
                    }
                }
            }
        }
        return false;
    }

    public User getUser(int id) {
        GameClient authenticatedClient = this.authenticatedClients.get(id);
        if (authenticatedClient != null) {
            User authenticatedUser = authenticatedClient.getUser();
            if (authenticatedUser != null
                    && authenticatedUser.getUserInfo() != null
                    && authenticatedUser.getUserInfo().getId() == id) {
                return authenticatedUser;
            }
        }

        for (GameClient client : this.clients.values()) {
            if (client.getUser() == null) continue;

            if (client.getUser().getUserInfo().getId() == id) return client.getUser();
        }

        return null;
    }

    public User getUser(String username) {
        for (GameClient client : this.clients.values()) {
            if (client.getUser() == null) continue;

            if (client.getUser().getUserInfo().getUsername().equalsIgnoreCase(username)) return client.getUser();
        }

        return null;
    }

    public List<User> getUsersWithIP(String ip) {
        List<User> users = new ArrayList<>();

        for (GameClient client : this.clients.values()) {
            if (client.getUser() != null && client.getUser().getUserInfo() != null) {
                if (client.getUser().getUserInfo().getIpLogin().equalsIgnoreCase(ip)) {
                    users.add(client.getUser());
                }
            }
        }

        return users;
    }

    /**
     * Find an existing GameClient that authenticated with the given SSO ticket.
     * Used to detect reconnections where the old connection hasn't been closed yet.
     */
    public GameClient findClientBySsoTicket(String ssoTicket) {
        if (ssoTicket == null || ssoTicket.isEmpty()) return null;

        for (GameClient client : this.clients.values()) {
            if (ssoTicket.equals(client.getSsoTicket()) && client.getUser() != null) {
                return client;
            }
        }
        return null;
    }

    public List<User> getUsersWithMachineId(String machineId) {
        List<User> users = new ArrayList<>();

        for (GameClient client : this.clients.values()) {
            if (client.getUser() != null
                    && client.getUser().getUserInfo() != null
                    && client.getMachineId().equalsIgnoreCase(machineId)) {
                users.add(client.getUser());
            }
        }

        return users;
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
            if (client.equals(exclude)) continue;

            client.sendResponse(message);
        }
    }

    public void sendBroadcastResponse(ServerMessage message, String minPermission, GameClient exclude) {
        for (GameClient client : this.clients.values()) {
            if (client.equals(exclude)) continue;

            if (client.getUser() != null) {
                if (client.getUser().hasPermission(minPermission)) {
                    client.sendResponse(message);
                }
            }
        }
    }

    public void CFKeepAlive() {
        Emulator.getThreading()
                .run(
                        () -> {
                            for (GameClient client : this.clients.values()) {
                                if (client != null && client.getChannel().isActive()) {
                                    client.sendKeepAlive();
                                }
                            }
                            CFKeepAlive();
                        },
                        30000);
    }
}
