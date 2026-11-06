package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserLoginEvent extends UserEvent {

    public final String ip;

    public UserLoginEvent(User user, String ip) {
        super(user);

        this.ip = ip;
    }
}
