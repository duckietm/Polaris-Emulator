package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.InteractionWiredLink;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.link.WiredLinkService;
import com.eu.habbo.messages.outgoing.wired.WiredLinkOperationResultComposer;

/** WIRED Link create network (AIR 2192): {@code int furniId, String name}. */
public class WiredLinkCreateNetworkEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return WiredLinkService.OP_CREATE;
    }

    @Override
    protected void handleLink(Room room, Habbo habbo, InteractionWiredLink link) {
        String name = this.packet.readString();
        this.client.sendResponse(new WiredLinkOperationResultComposer(
                WiredLinkService.get().create(habbo.getHabboInfo().getId(), room.getId(), link.getId(), name)));
    }
}
