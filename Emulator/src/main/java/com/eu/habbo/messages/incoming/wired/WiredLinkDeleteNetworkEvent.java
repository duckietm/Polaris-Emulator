package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.InteractionWiredLink;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.link.WiredLinkService;
import com.eu.habbo.messages.outgoing.wired.WiredLinkNetworkInvalidatedComposer;
import com.eu.habbo.messages.outgoing.wired.WiredLinkOperationResultComposer;

/**
 * WIRED Link delete network (AIR 2731): {@code int furniId, String networkId}. Its Links in every room are
 * disconnected. A Link window the user has open on another Link is told with the invalidated push (999).
 */
public class WiredLinkDeleteNetworkEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return WiredLinkService.OP_DELETE;
    }

    @Override
    protected void handleLink(Room room, Habbo habbo, InteractionWiredLink link) {
        String networkId = this.packet.readString();
        int ownerId = habbo.getHabboInfo().getId();
        WiredLinkService service = WiredLinkService.get();
        WiredLinkService.Result result = service.delete(ownerId, room.getId(), link.getId(), networkId);
        this.client.sendResponse(new WiredLinkOperationResultComposer(result));
        WiredLinkService.OpenTarget open = service.openTarget(ownerId);
        if (result.succeeded() && open != null && open.furniId() != link.getId()) {
            this.client.sendResponse(
                    new WiredLinkNetworkInvalidatedComposer(open.roomId(), open.furniId(), result.removedNetworkId()));
        }
    }
}
