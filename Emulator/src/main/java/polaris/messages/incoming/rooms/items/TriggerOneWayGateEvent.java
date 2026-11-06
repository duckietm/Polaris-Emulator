package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.InteractionOneWayGate;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class TriggerOneWayGateEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        UserItem item = this.client.getUser().getUserInfo().getCurrentRoom().getUserItem(itemId);

        if (item == null) return;

        if (item instanceof InteractionOneWayGate) {
            if (!item.getExtradata().equals("0") || this.client.getUser().getRoomUnit().isTeleporting) return;

            item.onClick(this.client, this.client.getUser().getUserInfo().getCurrentRoom(), null);
        }
    }
}
