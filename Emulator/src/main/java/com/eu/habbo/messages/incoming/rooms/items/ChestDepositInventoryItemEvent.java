package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestDepositSession;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniDepositHelper;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

/** Deposit one inventory furni row into a wired furni chest [chestItemId, inventoryItemId]. Header 9325. */
public class ChestDepositInventoryItemEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int chestItemId = this.packet.readInt();
        int inventoryItemId = this.packet.readInt();
        if (inventoryItemId <= 0) return;

        if (!ChestDepositSession.isDepositingInto(habbo.getHabboInfo().getId(), chestItemId)) return;

        HabboItem item = room.getHabboItem(chestItemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        HabboItem inventoryItem = habbo.getInventory().getItemsComponent().getHabboItem(inventoryItemId);
        if (inventoryItem == null) return;

        ChestFurniDepositHelper.depositInventoryItem(habbo, chest, inventoryItem);
    }
}
