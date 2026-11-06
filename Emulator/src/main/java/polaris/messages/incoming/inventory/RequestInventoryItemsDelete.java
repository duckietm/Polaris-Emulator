package polaris.messages.incoming.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.inventory.RemoveUserItemsComposer;
import polaris.threading.runnables.QueryDeleteUserItems;

public class RequestInventoryItemsDelete extends MessageHandler {
    private static final int MAX_DELETE_AMOUNT = 1000;

    public int getRatelimit() {
        return 500;
    }

    public void handle() {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int amount = this.packet.readInt();

        if (amount <= 0 || amount > MAX_DELETE_AMOUNT) return;

        UserItem userItem =
                this.client.getUser().getInventory().getItemsComponent().getUserItem(itemId);
        if (userItem == null) return;
        Item item = userItem.getBaseItem();
        if (item == null) return;
        if (!hasFurnitureInInventory(this.client.getUser(), item, amount)) return;
        final User user = this.client.getUser();
        if (user == null) return;
        Map<Integer, UserItem> toRemove = new HashMap<>();
        for (int i = 0; i < amount; i++) {
            UserItem userInventoryItem = user.getInventory().getItemsComponent().getAndRemoveUserItem(item);
            if (userInventoryItem != null) toRemove.put(userInventoryItem.getId(), userInventoryItem);
        }
        List<Integer> removedItemIds = new ArrayList<>();
        toRemove.values().forEach(object -> removedItemIds.add(object.getGiftAdjustedId()));
        user.getClient().sendResponse(new RemoveUserItemsComposer(removedItemIds));
        user.getClient().sendResponse(new InventoryRefreshComposer());
        Emulator.getThreading().runPersistence(new QueryDeleteUserItems(toRemove.values()));
    }

    private boolean hasFurnitureInInventory(User user, Item item, Integer amount) {
        int count = 0;
        for (UserItem userItem : user.getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (userItem.getBaseItem().getId() == item.getId()) count++;
        }
        return count >= amount;
    }
}
