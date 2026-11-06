package polaris.plugin.events.inventory;

import polaris.hotel.users.UserInventory;
import polaris.hotel.users.UserItem;

public class InventoryItemAddedEvent extends InventoryItemEvent {
    public InventoryItemAddedEvent(UserInventory inventory, UserItem item) {
        super(inventory, item);
    }
}
