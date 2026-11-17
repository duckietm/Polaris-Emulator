package com.srth.plugin.messages.incoming;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.srth.plugin.ActionItems.RoleplayFurniMenuManager;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager.RoleplayMenu;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.messages.outgoing.RoleplayFurniContextMenuComposer;
import gnu.trove.map.hash.THashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleplayFurniActionEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoleplayFurniActionEvent.class);

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() {
        if (this.client == null || this.client.getHabbo() == null) return;

        int objectId = this.packet.readInt();
        String actionId = this.packet.readString();

        if (objectId <= 0 || actionId.isBlank()) return;

        Room room = this.currentRoom();
        HabboItem furni = room == null ? null : room.getHabboItem(objectId);

        if (furni == null) return;

        RoleplayFurniMenuManager manager = RoleplayWebsocketsRoomsInfo.getRoleplayFurniMenuManager();
        if (manager == null) return;

        RoleplayMenu menu = manager.getContextMenu(furni.getBaseItem().getName(), this.client.getHabbo());

        if (RoleplayMenu.isNavigationAction(actionId)) {
            RoleplayMenu.Navigation navigation =
                    RoleplayMenu.parseNavigation(actionId).orElse(null);
            if (navigation == null || menu == null || menu.page(navigation.requestId(), navigation.path()) == null)
                return;

            this.client.sendResponse(new RoleplayFurniContextMenuComposer(
                    navigation.requestId(), objectId, furni.getBaseItem().getName(), menu, navigation.path()));
            return;
        }

        if (menu == null || !menu.containsEnabledAction(actionId)) return;

        THashMap<String, Object> actionData = new THashMap<>();
        actionData.put("name", actionId);
        actionData.put("item", Integer.toString(objectId));

        LOGGER.info(
                "[RP Roleplay Furni Menu] Executing action '{}' for furni '{}' (object={}).",
                actionId,
                furni.getBaseItem().getName(),
                objectId);
        RoleplayWebsocketsRoomsInfo.getItemActionManager().getIncomingActions(this.client, actionData);
    }
}
