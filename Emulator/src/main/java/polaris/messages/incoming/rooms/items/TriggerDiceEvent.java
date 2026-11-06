package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.InteractionDice;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class TriggerDiceEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) {
            return;
        }

        UserItem item = room.getUserItem(itemId);

        if (item != null) {
            if (item instanceof InteractionDice) {
                if (RoomLayout.tilesAdjecent(
                        room.getLayout().getTile(item.getX(), item.getY()),
                        this.client.getUser().getRoomUnit().getCurrentLocation())) {
                    item.onClick(this.client, room, new Object[] {});
                }
            }
        }
    }
}
