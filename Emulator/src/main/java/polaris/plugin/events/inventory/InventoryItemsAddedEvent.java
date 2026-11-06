package polaris.plugin.events.inventory;

import java.util.Set;
import polaris.hotel.users.UserInventory;
import polaris.hotel.users.UserItem;

public class InventoryItemsAddedEvent extends InventoryEvent {
    public final Set<UserItem> items;

    public InventoryItemsAddedEvent(UserInventory inventory, Set<UserItem> items) {
        super(inventory);
        this.items = items;
    }
}
