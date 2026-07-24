package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RoomFloorItemsComposer;
import java.util.Collection;

final class RoomWiredVisibilityService {

    private final Room room;

    RoomWiredVisibilityService(Room room) {
        this.room = room;
    }

    void setHidden(boolean hidden) {
        this.room.updateHideWiredState(hidden);
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (hidden) {
            this.remove(specialTypes.getTriggers());
            this.remove(specialTypes.getEffects());
            this.remove(specialTypes.getConditions());
            this.remove(specialTypes.getExtras());
        } else {
            this.publish(specialTypes.getTriggers());
            this.publish(specialTypes.getEffects());
            this.publish(specialTypes.getConditions());
            this.publish(specialTypes.getExtras());
        }
    }

    private void remove(Collection<? extends HabboItem> items) {
        for (HabboItem item : items) {
            this.room.sendComposer(new RemoveFloorItemComposer(item).compose());
        }
    }

    private void publish(Collection<? extends HabboItem> items) {
        this.room.sendComposer(new RoomFloorItemsComposer(this.room.getFurniOwnerNames(), items).compose());
    }
}
