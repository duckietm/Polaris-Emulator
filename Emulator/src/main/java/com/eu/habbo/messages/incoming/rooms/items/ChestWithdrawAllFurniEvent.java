package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniPackets;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;

import java.util.List;

/**
 * Withdraw all stored furni from a wired furni chest (official Vefehonuj wire shape):
 * {@code int chestItemId} only. Header 9326.
 */
public class ChestWithdrawAllFurniEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = this.packet.readInt();

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        if (!room.hasRights(habbo)) return;

        ChestStorage contents = chest.getContents();
        List<ChestFurniStoredItem> removedItems = contents.removeAllFurniItems();
        if (removedItems.isEmpty()) return;

        int taken = 0;
        for (ChestFurniStoredItem stored : removedItems) {
            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(stored.baseItemId);
            if (baseItem == null) continue;

            HabboItem created = Emulator.getGameEnvironment().getItemManager().createItem(
                    habbo.getHabboInfo().getId(), baseItem, stored.limitedStack, stored.limitedSells, stored.extradata);
            if (created == null) continue;

            this.client.sendResponse(new AddHabboItemComposer(created));
            habbo.getInventory().getItemsComponent().addItem(created);
            taken++;
        }

        if (taken <= 0) return;

        this.client.sendResponse(new InventoryRefreshComposer());

        contents.addLog(new ChestStorage.LogEntry("withdraw", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), taken, 0));
        chest.persistContents();

        this.client.sendResponse(new ChestDataComposer(chest));
        ChestFurniPackets.sendDelta(this.client, chest.getId(),
                removedItems.stream().map(i -> i.inventoryId).toList(), List.of());
    }
}
