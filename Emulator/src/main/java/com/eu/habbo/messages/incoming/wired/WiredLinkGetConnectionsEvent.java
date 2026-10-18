package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.InteractionWiredLink;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.link.WiredLinkService;
import com.eu.habbo.messages.outgoing.wired.WiredLinkConnectionsComposer;
import com.eu.habbo.messages.outgoing.wired.WiredLinkOperationResultComposer;

/**
 * WIRED Link connections request (AIR 2300, ours {@code Incoming.WiredLinkGetConnectionsEvent} since 2300 is
 * taken): {@code int furniId, String networkId}; answered with the connections (3962), or a failed result for
 * operation 5.
 */
public class WiredLinkGetConnectionsEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return WiredLinkService.OP_CONNECTIONS;
    }

    @Override
    protected void handleLink(Room room, Habbo habbo, InteractionWiredLink link) {
        String networkId = this.packet.readString();
        Object answer =
                WiredLinkService.get().connections(habbo.getHabboInfo().getId(), room.getId(), link.getId(), networkId);
        if (answer instanceof WiredLinkService.Connections connections) {
            this.client.sendResponse(new WiredLinkConnectionsComposer(connections));
        } else if (answer instanceof WiredLinkService.Result result) {
            this.client.sendResponse(new WiredLinkOperationResultComposer(result));
        }
    }
}
