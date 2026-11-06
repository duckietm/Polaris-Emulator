package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserRegisteredEvent extends UserEvent {

    public UserRegisteredEvent(User user) {
        super(user);
    }
}
