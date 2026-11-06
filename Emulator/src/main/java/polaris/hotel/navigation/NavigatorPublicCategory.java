package polaris.hotel.navigation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import polaris.hotel.rooms.Room;

public class NavigatorPublicCategory {
    public final int id;
    public final String name;
    public final List<Room> rooms;
    public final ListMode image;
    public final int order;

    public NavigatorPublicCategory(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name");
        this.image = set.getString("image").equals("1") ? ListMode.THUMBNAILS : ListMode.LIST;
        this.order = set.getInt("order_num");
        this.rooms = new ArrayList<>();
    }

    public void addRoom(Room room) {
        room.preventUncaching = true;
        this.rooms.add(room);
    }

    public void removeRoom(Room room) {
        this.rooms.remove(room);
        room.preventUncaching = room.isPublicRoom();
    }
}
