package com.eu.habbo.plugin.events.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.events.users.UserEvent;

import java.util.Set;

public class RoomFloorItemsLoadEvent extends UserEvent {
    private Set<HabboItem> floorItems;
    private boolean changedFloorItems;

    public RoomFloorItemsLoadEvent(Habbo habbo, Set<HabboItem> floorItems) {
        super(habbo);
        this.floorItems = floorItems;
        this.changedFloorItems = false;
    }

    public void setFloorItems(Set<HabboItem> floorItems) {
        this.changedFloorItems = true;
        this.floorItems = floorItems;
    }

    public boolean hasChangedFloorItems() {
        return this.changedFloorItems;
    }

    public Set<HabboItem> getFloorItems() {
        return this.floorItems;
    }
}
