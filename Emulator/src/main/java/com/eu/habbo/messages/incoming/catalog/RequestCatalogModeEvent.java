package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.habbohotel.catalog.CatalogNewAdditions;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.CatalogPagesListComposer;

public class RequestCatalogModeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String MODE = this.packet.readString();
        boolean normal = MODE.equalsIgnoreCase("normal");
        boolean newAdditions = normal && CatalogNewAdditions.get().available(this.client.getHabbo());
        this.client.sendResponse(new CatalogPagesListComposer(this.client.getHabbo(), MODE, newAdditions));

        if (!normal) {
            BuildersClubRoomSupport.sendPlacementStatus(this.client.getHabbo());
        }
    }
}
