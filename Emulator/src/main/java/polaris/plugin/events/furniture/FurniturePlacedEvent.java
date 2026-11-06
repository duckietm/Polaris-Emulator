package polaris.plugin.events.furniture;

import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class FurniturePlacedEvent extends FurnitureUserEvent {

    public final RoomTile location;
    private boolean pluginHelper;

    public FurniturePlacedEvent(UserItem furniture, User user, RoomTile location) {
        super(furniture, user);

        this.location = location;
        this.pluginHelper = false;
    }

    public void setPluginHelper(boolean helper) {
        this.pluginHelper = helper;
    }

    public boolean hasPluginHelper() {
        return this.pluginHelper;
    }
}
