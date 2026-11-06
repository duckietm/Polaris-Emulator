package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserRankChangedEvent extends UserEvent {

    public UserRankChangedEvent(User user) {
        super(user);
    }
}
