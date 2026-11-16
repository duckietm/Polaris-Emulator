package com.srth.plugin.websocket.communication.incoming.messages;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.Habbo.SSOClientManager;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSOTicketEvent extends IncomingMessage {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSOTicketEvent.class);

    public SSOTicketEvent(String header, THashMap<String, Object> data, Channel client) {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() {
        String sso = (String) data.get("SSO");
        SSOClientManager.addClient(sso, this.client);

        HabboClient roleplayClient = SSOClientManager.bindToExistingSession(sso, this.client);
        if (roleplayClient == null) {
            return;
        }

        this.client.eventLoop().schedule(() -> this.restoreRoleplayInterface(roleplayClient), 1, TimeUnit.SECONDS);

        // client.writeAndFlush(new BinaryWebSocketFrame(client.alloc().buffer().writeBytes(bytes)));
    }

    private void restoreRoleplayInterface(HabboClient roleplayClient) {
        Habbo habbo = roleplayClient.getHabbo();
        if (!this.client.isActive()
                || habbo == null
                || HabboClientManager.getHabboClient(habbo) != roleplayClient
                || !habbo.isOnline()) {
            return;
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();
        int roleplayCategory = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id");
        if (room == null || room.getCategory() != roleplayCategory) {
            return;
        }

        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp == null || habboRp.getHabboTutorial() == null) {
                return;
            }

            boolean showRoleplay = habboRp.getHabboTutorial().getCompleted() && !habboRp.getConstructorSalas();
            roleplayClient.send(new MessageManager(
                    "MessageManager", showRoleplay, showRoleplay ? Outcoming.EnterRoomRP : Outcoming.ExitRoom, null));
            RoleplayWebsocketsRoomsInfo.getConsolaManager().showConsola(habbo);
        } catch (SQLException e) {
            LOGGER.error("No se pudo restaurar la interfaz de roleplay al conectar el websocket.", e);
        }
    }
}
