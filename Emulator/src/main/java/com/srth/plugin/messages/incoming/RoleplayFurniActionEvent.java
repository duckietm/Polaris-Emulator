package com.srth.plugin.messages.incoming;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.srth.plugin.ActionItems.RoleplayFurniMenuManager;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
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

        RoleplayFurniMenuManager.Menu menu =
                RoleplayWebsocketsRoomsInfo.getRoleplayFurniMenuManager()
                        .getMenu(furni.getBaseItem().getName(), this.client.getHabbo());

        if (menu == null || menu.buttons().stream().noneMatch((button) -> button.actionId().equals(actionId))) return;

        THashMap<String, Object> actionData = new THashMap<>();
        actionData.put("name", actionId);
        actionData.put("item", Integer.toString(objectId));

        LOGGER.info("[RP Roleplay Furni Menu] Executing action '{}' for furni '{}' (object={}).",
                actionId,
                furni.getBaseItem().getName(),
                objectId);
        RoleplayWebsocketsRoomsInfo.getItemActionManager().getIncomingActions(this.client, actionData);
    }
}
