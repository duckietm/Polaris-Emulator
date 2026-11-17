package com.srth.plugin.messages.incoming;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.srth.plugin.ActionUsers.ManagerClickHabbos;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.messages.outgoing.RoleplayAvatarContextMenuComposer;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleplayAvatarContextMenuRequestEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoleplayAvatarContextMenuRequestEvent.class);

    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() {
        if (this.client == null || this.client.getHabbo() == null) return;

        int requestId = this.packet.readInt();
        int targetType = this.packet.readInt();
        int targetId = this.packet.readInt();
        if (requestId <= 0 || targetId <= 0) return;
        if (targetType != RoleplayAvatarMenuManager.TARGET_USER && targetType != RoleplayAvatarMenuManager.TARGET_BOT)
            return;

        RoleplayAvatarMenuManager manager = RoleplayWebsocketsRoomsInfo.getRoleplayAvatarMenuManager();
        Room room = this.currentRoom();
        boolean isRoleplayRoom = manager != null && manager.isRoleplayRoom(room);
        RoleplayAvatarMenuManager.Menu menu =
                isRoleplayRoom ? new RoleplayAvatarMenuManager.Menu("Acciones", List.of()) : null;

        if (isRoleplayRoom) {
            if (targetType == RoleplayAvatarMenuManager.TARGET_USER) {
                Habbo target = room.getHabbo(targetId);
                if (target != null) {
                    try {
                        menu = manager.getUserMenu(target, this.client.getHabbo());
                    } catch (SQLException exception) {
                        LOGGER.error("Unable to create the roleplay avatar menu for user {}.", targetId, exception);
                    }
                }

                this.hideLegacyUserActions();
            } else {
                Bot target = room.getBot(targetId);
                if (target != null) menu = manager.getBotMenu(target);
            }
        }

        this.client.sendResponse(
                new RoleplayAvatarContextMenuComposer(requestId, targetType, targetId, isRoleplayRoom, menu));
    }

    private void hideLegacyUserActions() {
        HabboClient habboClient = HabboClientManager.getHabboClient(this.client.getHabbo());
        if (habboClient == null) return;

        try {
            new ManagerClickHabbos().ocultButtonsUsarOther(habboClient);
        } catch (SQLException exception) {
            LOGGER.error(
                    "Unable to hide legacy roleplay user actions for {}.",
                    this.client.getHabbo().getHabboInfo().getUsername(),
                    exception);
        }
    }
}
