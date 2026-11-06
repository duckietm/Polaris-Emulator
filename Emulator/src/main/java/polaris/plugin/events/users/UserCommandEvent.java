package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserCommandEvent extends UserEvent {

    public final String[] args;

    public final boolean succes;

    public UserCommandEvent(User user, String[] args, boolean succes) {
        super(user);
        this.args = args;
        this.succes = succes;
    }
}
