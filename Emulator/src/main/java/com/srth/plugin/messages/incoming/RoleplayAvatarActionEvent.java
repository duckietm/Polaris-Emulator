package com.srth.plugin.messages.incoming;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.srth.plugin.ActionUsers.ManagerClickHabbos;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager.RoleplayMenu;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.messages.outgoing.RoleplayAvatarContextMenuComposer;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import gnu.trove.map.hash.THashMap;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleplayAvatarActionEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoleplayAvatarActionEvent.class);

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() {
        if (this.client == null || this.client.getHabbo() == null) return;

        int targetType = this.packet.readInt();
        int targetId = this.packet.readInt();
        String actionId = this.packet.readString();
        if (targetId <= 0 || actionId.isBlank()) return;

        RoleplayAvatarMenuManager manager = RoleplayWebsocketsRoomsInfo.getRoleplayAvatarMenuManager();
        Room room = this.currentRoom();
        if (manager == null || !manager.isRoleplayRoom(room)) return;

        if (targetType == RoleplayAvatarMenuManager.TARGET_USER) {
            this.executeUserAction(room, manager, targetId, actionId);
        } else if (targetType == RoleplayAvatarMenuManager.TARGET_BOT) {
            this.executeBotAction(room, manager, targetId, actionId);
        }
    }

    private void executeUserAction(Room room, RoleplayAvatarMenuManager manager, int targetId, String actionId) {
        Habbo actor = this.client.getHabbo();
        Habbo target = room.getHabbo(targetId);
        if (target == null || target == actor) return;

        RoleplayMenu menu;
        try {
            menu = manager.getUserContextMenu(target, actor);
        } catch (SQLException exception) {
            LOGGER.error("Unable to validate roleplay avatar action '{}' for user {}.", actionId, targetId, exception);
            return;
        }

        if (RoleplayMenu.isNavigationAction(actionId)) {
            RoleplayMenu.Navigation navigation =
                    RoleplayMenu.parseNavigation(actionId).orElse(null);
            if (navigation == null || menu.page(navigation.requestId(), navigation.path()) == null) return;

            this.client.sendResponse(new RoleplayAvatarContextMenuComposer(
                    navigation.requestId(),
                    RoleplayAvatarMenuManager.TARGET_USER,
                    targetId,
                    true,
                    menu,
                    navigation.path()));
            return;
        }

        if (!menu.containsEnabledAction(actionId)) return;

        HabboClient habboClient = HabboClientManager.getHabboClient(actor);
        if (habboClient == null) {
            LOGGER.warn(
                    "Cannot execute roleplay avatar action '{}' for user {}: RP websocket is unavailable.",
                    actionId,
                    targetId);
            return;
        }

        THashMap<String, Object> actionData = new THashMap<>();
        actionData.put("action", actionId);
        actionData.put("date", Integer.toString(targetId));
        try {
            new ManagerClickHabbos().incomingActionsReceibedOther(actionData, habboClient);
        } catch (SQLException exception) {
            LOGGER.error("Unable to execute roleplay avatar action '{}' for user {}.", actionId, targetId, exception);
        }
    }

    private void executeBotAction(Room room, RoleplayAvatarMenuManager manager, int targetId, String actionId) {
        Bot bot = room.getBot(targetId);
        RoleplayMenu menu = manager.getBotContextMenu(bot);
        if (RoleplayMenu.isNavigationAction(actionId)) {
            RoleplayMenu.Navigation navigation =
                    RoleplayMenu.parseNavigation(actionId).orElse(null);
            if (navigation == null || menu == null || menu.page(navigation.requestId(), navigation.path()) == null)
                return;

            this.client.sendResponse(new RoleplayAvatarContextMenuComposer(
                    navigation.requestId(),
                    RoleplayAvatarMenuManager.TARGET_BOT,
                    targetId,
                    true,
                    menu,
                    navigation.path()));
            return;
        }

        if (bot == null || !manager.executeBotAction(this.client.getHabbo(), bot, actionId)) {
            LOGGER.debug("Rejected roleplay bot action '{}' for bot {}.", actionId, targetId);
        }
    }
}
