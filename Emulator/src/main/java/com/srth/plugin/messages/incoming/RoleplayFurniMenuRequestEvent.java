package com.srth.plugin.messages.incoming;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.srth.plugin.ActionItems.RoleplayFurniMenuManager;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.messages.outgoing.RoleplayFurniContextMenuComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleplayFurniMenuRequestEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoleplayFurniMenuRequestEvent.class);

    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() {
        if (this.client == null || this.client.getHabbo() == null) return;

        int requestId = this.packet.readInt();
        int objectId = this.packet.readInt();

        if (requestId <= 0 || objectId <= 0) return;

        Room room = this.currentRoom();
        HabboItem furni = room == null ? null : room.getHabboItem(objectId);
        String furniName = furni == null ? "" : furni.getBaseItem().getName();
        RoleplayFurniMenuManager.Menu menu =
                RoleplayWebsocketsRoomsInfo.getRoleplayFurniMenuManager().getMenu(furniName, this.client.getHabbo());

        if (menu != null) {
            LOGGER.info(
                    "[RP Roleplay Furni Menu] Sending menu for object={} classname='{}' buttons={}.",
                    objectId,
                    furniName,
                    menu.buttons().size());
        }

        this.client.sendResponse(new RoleplayFurniContextMenuComposer(requestId, objectId, furniName, menu));
    }
}
