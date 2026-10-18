package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.InteractionWiredLink;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.link.WiredLinkService;
import com.eu.habbo.messages.outgoing.wired.WiredLinkOperationResultComposer;

/**
 * WIRED Link save selection (AIR 385): {@code int furniId, String networkId, bool receiveLocal}. Connects the
 * Link to one of the user's networks ("0" for none) and sets whether it receives its own room's messages.
 */
public class WiredLinkSaveSelectionEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return WiredLinkService.OP_SAVE;
    }

    @Override
    protected void handleLink(Room room, Habbo habbo, InteractionWiredLink link) {
        String networkId = this.packet.readString();
        boolean receiveLocal = this.packet.readBoolean();
        this.client.sendResponse(new WiredLinkOperationResultComposer(WiredLinkService.get()
                .save(habbo.getHabboInfo().getId(), room.getId(), link.getId(), networkId, receiveLocal)));
    }
}
