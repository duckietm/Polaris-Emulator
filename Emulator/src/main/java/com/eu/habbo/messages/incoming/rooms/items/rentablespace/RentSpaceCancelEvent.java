package com.eu.habbo.messages.incoming.rooms.items.rentablespace;

import com.eu.habbo.habbohotel.items.interactions.InteractionRentableSpace;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.rooms.items.RoomItemInputGuard;

public class RentSpaceCancelEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        HabboItem item = room.getHabboItem(itemId);

        if (room.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() ||
                this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            if (item instanceof InteractionRentableSpace) {
                ((InteractionRentableSpace) item).endRent();

                room.updateItem(item);
            }
        }
    }
}
