package polaris.messages.incoming.inventory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.InventoryItemsComposer;

public class RequestInventoryItemsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int totalItems = this.client
                .getUser()
                .getInventory()
                .getItemsComponent()
                .getItems()
                .size();

        if (totalItems == 0) {
            this.client.sendResponse(new InventoryItemsComposer(0, 1, new Int2ObjectOpenHashMap<>()));
            return;
        }

        int totalFragments = (int) Math.ceil((double) totalItems / 1000.0);

        if (totalFragments == 0) {
            totalFragments = 1;
        }

        synchronized (this.client.getUser().getInventory().getItemsComponent().getItems()) {
            Int2ObjectMap<UserItem> items = new Int2ObjectOpenHashMap<>();

            int count = 0;
            int fragmentNumber = 0;

            for (Int2ObjectMap.Entry<UserItem> itemEntry : this.client
                    .getUser()
                    .getInventory()
                    .getItemsComponent()
                    .getItems()
                    .int2ObjectEntrySet()) {
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
