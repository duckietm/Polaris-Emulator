package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.InteractionWiredLink;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.link.WiredLinkService;
import com.eu.habbo.messages.outgoing.wired.WiredLinkOperationResultComposer;

/** WIRED Link rename network (AIR 2794): {@code int furniId, String networkId, String name}. */
public class WiredLinkRenameNetworkEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return WiredLinkService.OP_RENAME;
    }

    @Override
    protected void handleLink(Room room, Habbo habbo, InteractionWiredLink link) {
        String networkId = this.packet.readString();
        String name = this.packet.readString();
        this.client.sendResponse(new WiredLinkOperationResultComposer(WiredLinkService.get()
                .rename(habbo.getHabboInfo().getId(), room.getId(), link.getId(), networkId, name)));
    }
}
