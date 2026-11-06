package polaris.plugin.events.furniture;

import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class FurnitureMovedEvent extends FurnitureUserEvent {

    public final RoomTile oldPosition;
    public final RoomTile newPosition;
    private boolean pluginHelper;

    public FurnitureMovedEvent(UserItem furniture, User user, RoomTile oldPosition, RoomTile newPosition) {
        super(furniture, user);

        this.oldPosition = oldPosition;
        this.newPosition = newPosition;
        this.pluginHelper = false;
    }

    public void setPluginHelper(boolean helper) {
        this.pluginHelper = helper;
    }

    public boolean hasPluginHelper() {
        return this.pluginHelper;
    }
}
