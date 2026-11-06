package polaris.plugin.events.rooms;

import java.util.Set;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.plugin.events.users.UserEvent;

public class RoomFloorItemsLoadEvent extends UserEvent {
    private Set<UserItem> floorItems;
    private boolean changedFloorItems;

    public RoomFloorItemsLoadEvent(User user, Set<UserItem> floorItems) {
        super(user);
        this.floorItems = floorItems;
        this.changedFloorItems = false;
    }

    public void setFloorItems(Set<UserItem> floorItems) {
        this.changedFloorItems = true;
        this.floorItems = floorItems;
    }

    public boolean hasChangedFloorItems() {
        return this.changedFloorItems;
    }

    public Set<UserItem> getFloorItems() {
        return this.floorItems;
    }
}
