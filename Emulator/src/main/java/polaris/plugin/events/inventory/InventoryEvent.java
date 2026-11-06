package polaris.plugin.events.inventory;

import polaris.hotel.users.UserInventory;
import polaris.plugin.Event;

public abstract class InventoryEvent extends Event {
    public final UserInventory inventory;

    protected InventoryEvent(UserInventory inventory) {
        this.inventory = inventory;
    }
}
