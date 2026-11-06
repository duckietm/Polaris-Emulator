package polaris.messages.incoming.rooms.items;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.items.interactions.InteractionRandomState;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class UseRandomStateItemEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(UseRandomStateItemEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        try {
            int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
            this.packet.readInt(); // state

            Room room = this.client.getUser().getUserInfo().getCurrentRoom();
            // Outside a room this threw and was logged as an error for every packet.
            if (room == null) return;

            UserItem item = room.getUserItem(itemId);

            if (item == null || !(item instanceof InteractionRandomState)) return;

            InteractionRandomState randomStateItem = (InteractionRandomState) item;
            randomStateItem.onRandomStateClick(this.client, room);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }
}
