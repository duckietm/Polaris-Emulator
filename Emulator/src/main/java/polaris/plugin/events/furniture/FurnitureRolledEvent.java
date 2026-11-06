package polaris.plugin.events.furniture;

import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;

public class FurnitureRolledEvent extends FurnitureEvent {

    public final UserItem roller;

    public final RoomTile newLocation;

    public FurnitureRolledEvent(UserItem furniture, UserItem roller, RoomTile newLocation) {
        super(furniture);

        this.roller = roller;
        this.newLocation = newLocation;
    }
}
