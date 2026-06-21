package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class RoomPickupChooserEvent extends MessageHandler {
    private static final int MAX_PICKUP_CHOOSER_ITEMS = 100;

    @Override
    public void handle() throws Exception {
        int count = this.packet.readInt();

        if (count <= 0 || count > MAX_PICKUP_CHOOSER_ITEMS)
            return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        for (int i = 0; i < count; i++) {
            int itemId = this.packet.readInt();
            HabboItem item = room.getHabboItem(itemId);

            if (item != null) {
                if (item instanceof InteractionPostIt)
                    continue;

                if (item.getUserId() == this.client.getHabbo().getHabboInfo().getId()) {
                    room.pickUpItem(item, this.client.getHabbo());
                } else {
                    if (room.hasRights(this.client.getHabbo())) {
                        if (this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)) {
                            item.setUserId(this.client.getHabbo().getHabboInfo().getId());
                        } else if (this.client.getHabbo().getHabboInfo().getId() != room.getOwnerId() && item.getUserId() == room.getOwnerId()) {
                            continue;
                        }

                        room.ejectUserItem(item);
                    }
                }
            }
        }
    }
}
