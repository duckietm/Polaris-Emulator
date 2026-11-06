package polaris.plugin.events.furniture;

import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class FurnitureRotatedEvent extends FurnitureUserEvent {

    public final int oldRotation;

    public FurnitureRotatedEvent(UserItem furniture, User user, int oldRotation) {
        super(furniture, user);

        this.oldRotation = oldRotation;
    }
}
