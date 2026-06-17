package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class RoomPickupItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); //10 = floorItem and 20 = wallItem
        int itemId = this.packet.readInt();

        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        HabboItem item = room.getHabboItem(itemId);

        if (item == null)
            return;

        if (item instanceof InteractionPostIt)
            return;

        if (item.getUserId() == this.client.getHabbo().getHabboInfo().getId()) {
            room.pickUpItem(item, this.client.getHabbo());
        } else {
            if (room.hasRights(this.client.getHabbo())) {
                if (this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)) {
                    item.setUserId(this.client.getHabbo().getHabboInfo().getId());
                } else if (this.client.getHabbo().getHabboInfo().getId() != room.getOwnerId() && item.getUserId() == room.getOwnerId()) {
                    return;
                }

                room.ejectUserItem(item);
            }
        }
    }
}
