package com.eu.habbo.messages.incoming.inventory;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.InventoryItemsComposer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class RequestInventoryItemsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int totalItems = this.client.getHabbo().getInventory().getItemsComponent().getItems().size();

        if (totalItems == 0) {
            this.client.sendResponse(new InventoryItemsComposer(0, 1, new Int2ObjectOpenHashMap<>()));
            return;
        }

        int totalFragments = (int) Math.ceil((double) totalItems / 1000.0);

        if (totalFragments == 0) {
            totalFragments = 1;
        }

        synchronized (this.client.getHabbo().getInventory().getItemsComponent().getItems()) {
            Int2ObjectMap<HabboItem> items = new Int2ObjectOpenHashMap<>();

            int count = 0;
            int fragmentNumber = 0;

            for (Int2ObjectMap.Entry<HabboItem> itemEntry : this.client.getHabbo().getInventory().getItemsComponent().getItems().int2ObjectEntrySet()) {
                if (count == 0) {
                    fragmentNumber++;
                }

                items.put(itemEntry.getIntKey(), itemEntry.getValue());
                count++;

                if (count == 1000) {
                    this.client.sendResponse(new InventoryItemsComposer(fragmentNumber, totalFragments, items));
                    count = 0;
                    items = new Int2ObjectOpenHashMap<>();
                }
            }

            if (count > 0 && !items.isEmpty()) {
                this.client.sendResponse(new InventoryItemsComposer(fragmentNumber, totalFragments, items));
            }
        }
    }
}
