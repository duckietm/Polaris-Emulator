package polaris.plugin.events.navigator;

import java.util.ArrayList;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public class NavigatorPopularRoomsEvent extends NavigatorRoomsEvent {

    public final ArrayList<Room> rooms;

    public NavigatorPopularRoomsEvent(User user, ArrayList<Room> rooms) {
        super(user, rooms);

        this.rooms = rooms;
    }
}
