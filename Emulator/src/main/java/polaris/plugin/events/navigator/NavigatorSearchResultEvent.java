package polaris.plugin.events.navigator;

import java.util.ArrayList;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public class NavigatorSearchResultEvent extends NavigatorRoomsEvent {

    public final String prefix;

    public final String query;

    public NavigatorSearchResultEvent(User user, String prefix, String query, ArrayList<Room> rooms) {
        super(user, rooms);

        this.prefix = prefix;
        this.query = query;
    }
}
