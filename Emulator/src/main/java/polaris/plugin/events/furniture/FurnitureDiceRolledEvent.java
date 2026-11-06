package polaris.plugin.events.furniture;

import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class FurnitureDiceRolledEvent extends FurnitureUserEvent {

    public int result;

    public FurnitureDiceRolledEvent(UserItem furniture, User user, int result) {
        super(furniture, user);

        this.result = result;
    }
}
