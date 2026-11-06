package polaris.plugin.events.users.friends;

import polaris.hotel.users.User;
import polaris.plugin.events.users.UserEvent;

public class UserRequestFriendshipEvent extends UserEvent {

    public final String name;

    public final User friend;

    public UserRequestFriendshipEvent(User user, String name, User friend) {
        super(user);

        this.name = name;
        this.friend = friend;
    }
}
