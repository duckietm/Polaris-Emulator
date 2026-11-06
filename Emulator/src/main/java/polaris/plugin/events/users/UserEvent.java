package polaris.plugin.events.users;

import polaris.hotel.users.User;
import polaris.plugin.Event;

public abstract class UserEvent extends Event {

    public final User user;

    public UserEvent(User user) {
        this.user = user;
    }
}
