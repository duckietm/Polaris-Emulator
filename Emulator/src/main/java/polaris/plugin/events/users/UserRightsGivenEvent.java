package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserRightsGivenEvent extends UserEvent {
    public final User receiver;

    public UserRightsGivenEvent(User user, User receiver) {
        super(user);

        this.receiver = receiver;
    }
}
