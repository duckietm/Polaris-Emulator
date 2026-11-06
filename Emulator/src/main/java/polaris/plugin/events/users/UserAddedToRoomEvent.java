package polaris.plugin.events.users;

import java.util.Collection;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public class UserAddedToRoomEvent extends UserEvent {

    public final Room room;
    public Collection<User> usersToSendEnter;
    public Collection<User> visibleUsers;

    public UserAddedToRoomEvent(
            User user, Room room, Collection<User> usersToSendEnter, Collection<User> visibleUsers) {
        super(user);

        this.room = room;
        this.usersToSendEnter = usersToSendEnter;
        this.visibleUsers = visibleUsers;
    }
}
