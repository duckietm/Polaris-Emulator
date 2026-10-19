package com.eu.habbo.messages.incoming.rooms.items.rentable;

import com.eu.habbo.habbohotel.items.rentable.RentableFurnitureManager;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.rooms.items.RoomItemInputGuard;

/** ExtendRentOrBuyoutFurni (1071): extend or buy out a rented furni placed in the current room. */
public class ExtendRentOrBuyoutFurniEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        boolean isWallItem = this.packet.readBoolean();
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        boolean buyout = this.packet.readBoolean();

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        HabboItem item = room.getHabboItem(itemId);

        if (item == null) return;

        if (!RentableFurnitureManager.extendOrBuyout(this.client.getHabbo(), item, buyout)) return;

        room.updateItem(item);
    }
}
