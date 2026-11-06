package polaris.plugin.events.users.friends;

import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.users.User;

public class UserFriendChatEvent extends UserFriendEvent {

    public String message;

    public UserFriendChatEvent(User user, MessengerBuddy friend, String message) {
        super(user, friend);

        this.message = message;
    }
}
