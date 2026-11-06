package polaris.plugin.events.furniture;

import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class FurniturePickedUpEvent extends FurnitureUserEvent {

    public FurniturePickedUpEvent(UserItem furniture, User user) {
        super(furniture, user);
    }
}
