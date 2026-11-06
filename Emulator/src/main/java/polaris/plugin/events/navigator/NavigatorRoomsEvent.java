package polaris.plugin.events.navigator;

import java.util.ArrayList;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.plugin.events.users.UserEvent;

public abstract class NavigatorRoomsEvent extends UserEvent {

    public final ArrayList<Room> rooms;

    public NavigatorRoomsEvent(User user, ArrayList<Room> rooms) {
        super(user);

        this.rooms = rooms;
    }
}
