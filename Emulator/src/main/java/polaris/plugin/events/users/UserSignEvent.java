package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserSignEvent extends UserEvent {

    public int sign;

    public UserSignEvent(User user, int sign) {
        super(user);
        this.sign = sign;
    }
}
