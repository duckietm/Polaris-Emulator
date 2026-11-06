package polaris.messages.incoming.navigator;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.rooms.RoomCategory;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.NewNavigatorCategoryUserCountComposer;

/** Client requests per-category visitor counts (header 3782). */
public class GetCategoriesWithUserCountEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        List<RoomCategory> categories = new ArrayList<>(
                Emulator.getGameEnvironment().getRoomManager().roomCategoriesForUser(this.client.getUser()));
        this.client.sendResponse(new NewNavigatorCategoryUserCountComposer(categories));
    }
}
