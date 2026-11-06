package polaris.plugin.events.users.friends;

import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.users.User;
import polaris.plugin.events.users.UserEvent;

public abstract class UserFriendEvent extends UserEvent {
    public final MessengerBuddy friend;

    public UserFriendEvent(User user, MessengerBuddy friend) {
        super(user);

        this.friend = friend;
    }
}
