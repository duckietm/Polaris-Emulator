package polaris.messages.incoming.catalog;

import polaris.hotel.catalog.CatalogNewAdditions;
import polaris.hotel.rooms.BuildersClubRoomSupport;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.CatalogPagesListComposer;

public class RequestCatalogModeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String MODE = this.packet.readString();
        boolean normal = MODE.equalsIgnoreCase("normal");
        boolean newAdditions = normal && CatalogNewAdditions.get().available(this.client.getUser());
        this.client.sendResponse(new CatalogPagesListComposer(this.client.getUser(), MODE, newAdditions));

        if (!normal) {
            BuildersClubRoomSupport.sendPlacementStatus(this.client.getUser());
        }
    }
}
