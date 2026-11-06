package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserKickEvent extends UserEvent {

    public final User target;

    public UserKickEvent(User user, User target) {
        super(user);

        this.target = target;
    }
}
