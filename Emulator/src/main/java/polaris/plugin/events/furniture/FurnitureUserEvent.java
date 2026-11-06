package polaris.plugin.events.furniture;

import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public abstract class FurnitureUserEvent extends FurnitureEvent {

    public final User user;

    public FurnitureUserEvent(UserItem furniture, User user) {
        super(furniture);
        this.user = user;
    }
}
