package com.eu.habbo.messages.incoming.rooms.items.rentablespace;

import com.eu.habbo.habbohotel.items.interactions.InteractionRentableSpace;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.rooms.items.RoomItemInputGuard;

/** RentableSpaceCancelRent (1667): the "Cancel rental space (No refund)" button of the widget. */
public class RentSpaceCancelEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        HabboItem item = room.getHabboItem(itemId);

        if (!(item instanceof InteractionRentableSpace space)) return;

        if (!space.canCancelRent(this.client.getHabbo(), room)) return;

        space.endRent();
        room.updateItem(space);
        space.sendRentWidget(this.client.getHabbo());
    }
}
