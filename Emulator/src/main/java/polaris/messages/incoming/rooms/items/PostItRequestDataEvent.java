package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.PostItDataComposer;

public class PostItRequestDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {
            UserItem item = room.getUserItem(itemId);

            if (item instanceof InteractionPostIt) {
                this.client.sendResponse(new PostItDataComposer((InteractionPostIt) item));
            }
        }
    }
}
