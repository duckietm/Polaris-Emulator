package polaris.plugin.events.navigator;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.plugin.events.users.UserEvent;

public class NavigatorRoomCreatedEvent extends UserEvent {

    public final Room room;

    public NavigatorRoomCreatedEvent(User user, Room room) {
        super(user);

        this.room = room;
    }
}
