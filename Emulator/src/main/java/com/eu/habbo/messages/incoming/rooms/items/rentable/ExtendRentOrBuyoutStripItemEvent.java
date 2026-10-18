package com.eu.habbo.messages.incoming.rooms.items.rentable;

import com.eu.habbo.habbohotel.items.rentable.RentableFurnitureManager;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.rooms.items.RoomItemInputGuard;
import com.eu.habbo.messages.outgoing.inventory.InventoryUpdateItemComposer;

/** ExtendRentOrBuyoutStripItem (2115): extend or buy out a rented furni still in the inventory. */
public class ExtendRentOrBuyoutStripItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int stripId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        boolean buyout = this.packet.readBoolean();

        if (!RoomItemInputGuard.isPositiveId(stripId)) return;

        HabboItem item =
                this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(stripId);

        if (item == null) return;

        if (!RentableFurnitureManager.extendOrBuyout(this.client.getHabbo(), item, buyout)) return;

        this.client.sendResponse(new InventoryUpdateItemComposer(item));
    }
}
