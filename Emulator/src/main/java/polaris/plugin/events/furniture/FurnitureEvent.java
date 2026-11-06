package polaris.plugin.events.furniture;

import polaris.hotel.users.UserItem;
import polaris.plugin.Event;

public abstract class FurnitureEvent extends Event {

    public final UserItem furniture;

    public FurnitureEvent(UserItem furniture) {
        this.furniture = furniture;
    }
}
