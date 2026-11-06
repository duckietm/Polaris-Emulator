package polaris.messages.incoming.catalog;

import polaris.hotel.rooms.BuildersClubRoomSupport;
import polaris.messages.incoming.MessageHandler;

public class BuildersClubQueryFurniCountEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        BuildersClubRoomSupport.sendPlacementStatus(this.client.getUser());
    }
}
