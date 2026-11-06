package polaris.plugin.events.users;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public class UserEnterRoomEvent extends UserEvent {

    public final Room room;

    public UserEnterRoomEvent(User user, Room room) {
        super(user);

        this.room = room;
    }
}
