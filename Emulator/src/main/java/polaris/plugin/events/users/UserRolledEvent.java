package polaris.plugin.events.users;

import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class UserRolledEvent extends UserEvent {

    public final UserItem roller;

    public final RoomTile location;

    public UserRolledEvent(User user, UserItem roller, RoomTile location) {
        super(user);

        this.roller = roller;
        this.location = location;
    }
}
