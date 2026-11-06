package polaris.plugin.events.users;

import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;

public class UserTakeStepEvent extends UserEvent {

    public final RoomTile fromLocation;

    public final RoomTile toLocation;

    public UserTakeStepEvent(User user, RoomTile fromLocation, RoomTile toLocation) {
        super(user);

        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
    }
}
