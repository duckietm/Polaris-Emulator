package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserRespectedEvent extends UserEvent {
    public final User from;

    public UserRespectedEvent(User user, User from) {
        super(user);

        this.from = from;
    }
}
