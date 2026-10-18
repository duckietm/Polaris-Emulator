package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.InteractionWiredLink;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.link.WiredLinkService;
import com.eu.habbo.messages.outgoing.wired.WiredLinkConfigurationComposer;

/** WIRED Link configuration request (AIR 3208): {@code int furniId}; answered with the configuration (3405). */
public class WiredLinkRequestConfigEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return 0;
    }

    @Override
    protected void handleLink(Room room, Habbo habbo, InteractionWiredLink link) {
        this.client.sendResponse(new WiredLinkConfigurationComposer(
                WiredLinkService.get().configuration(habbo.getHabboInfo().getId(), room.getId(), link.getId())));
    }

    @Override
    public int getRatelimit() {
        return 0;
    }
}
