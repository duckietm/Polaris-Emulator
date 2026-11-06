package polaris.plugin.events.furniture;

import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class FurnitureToggleEvent extends FurnitureUserEvent {
    public int state;

    public FurnitureToggleEvent(UserItem furniture, User user, int state) {
        super(furniture, user);

        this.state = state;
    }
}
