package polaris.hotel.rooms;

import java.util.Collection;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.messages.outgoing.rooms.items.RoomFloorItemsComposer;

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

    private void remove(Collection<? extends UserItem> items) {
        for (UserItem item : items) {
            this.room.sendComposer(new RemoveFloorItemComposer(item).compose());
        }
    }

    private void publish(Collection<? extends UserItem> items) {
        this.room.sendComposer(new RoomFloorItemsComposer(this.room.getFurniOwnerNames(), items).compose());
    }
}
