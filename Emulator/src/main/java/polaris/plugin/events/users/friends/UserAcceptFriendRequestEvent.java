package polaris.plugin.events.users.friends;

import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.users.User;

public class UserAcceptFriendRequestEvent extends UserFriendEvent {

    public UserAcceptFriendRequestEvent(User user, MessengerBuddy friend) {
        super(user, friend);
    }
}
