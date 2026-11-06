package polaris.messages.incoming.navigator;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.rooms.RoomCategory;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.RoomCategoriesComposer;

public class RequestRoomCategoriesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        List<RoomCategory> roomCategoryList =
                Emulator.getGameEnvironment().getRoomManager().roomCategoriesForUser(this.client.getUser());
        this.client.sendResponse(new RoomCategoriesComposer(roomCategoryList));
        // this.client.sendResponse(new NewNavigatorEventCategoriesComposer());
    }
}
