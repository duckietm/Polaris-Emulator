package polaris.messages.incoming.inventory;

import java.util.ArrayList;
import java.util.List;
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

        final User user = this.client.getUser();
        if (user == null) return;
        UserItem userItem = user.getInventory().getItemsComponent().getUserItem(itemId);
        if (userItem == null || userItem.getBaseItem() == null) return;

        List<UserItem> selected = selectItemsToDelete(user, userItem, amount);
        if (selected == null) return;
        if (!user.getInventory().getItemsComponent().takeUserItemsAtomically(selected)) return;

        List<Integer> removedItemIds = new ArrayList<>();
        selected.forEach(object -> removedItemIds.add(object.getGiftAdjustedId()));
        user.getClient().sendResponse(new RemoveUserItemsComposer(removedItemIds));
        user.getClient().sendResponse(new InventoryRefreshComposer());
        Emulator.getThreading().runPersistence(new QueryDeleteUserItems(selected));
    }

    /**
     * The chosen item first, then other copies of the same base that stack with it in the client. A limited or rented
     * item is unique, so it is deleted only on its own: any other instance of the base could be the wrong one.
     */
    static List<UserItem> selectItemsToDelete(User user, UserItem chosen, int amount) {
        List<UserItem> selected = new ArrayList<>();
        selected.add(chosen);
        if (isUnique(chosen)) return amount == 1 ? selected : null;

        Item item = chosen.getBaseItem();
        for (UserItem other : user.getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (selected.size() >= amount) break;
            if (other == chosen || other.getBaseItem() != item || isUnique(other)) continue;
            selected.add(other);
        }
        return selected.size() == amount ? selected : null;
    }

    private static boolean isUnique(UserItem item) {
        return item.isLimited() || item.hasRentPeriod();
    }
}
