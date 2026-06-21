package com.eu.habbo.plugin.events.inventory;

import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.Set;

public class InventoryItemsAddedEvent extends InventoryEvent {
    public final Set<HabboItem> items;

    public InventoryItemsAddedEvent(HabboInventory inventory, Set<HabboItem> items) {
        super(inventory);
        this.items = items;
    }
}
