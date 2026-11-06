package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserDisconnectEvent extends UserEvent {

    public UserDisconnectEvent(User user) {
        super(user);
    }
}
