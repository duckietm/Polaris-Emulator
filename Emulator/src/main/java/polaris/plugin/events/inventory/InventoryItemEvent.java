package polaris.plugin.events.inventory;

import polaris.hotel.users.UserInventory;
import polaris.hotel.users.UserItem;

public class InventoryItemEvent extends InventoryEvent {
    public UserItem item;

    public InventoryItemEvent(UserInventory inventory, UserItem item) {
        super(inventory);

        this.item = item;
    }
}
