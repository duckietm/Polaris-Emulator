package polaris.plugin.events.users.friends;

import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.users.User;

public class UserRelationShipEvent extends UserFriendEvent {
    public int relationShip;

    public UserRelationShipEvent(User user, MessengerBuddy friend, int relationShip) {
        super(user, friend);

        this.relationShip = relationShip;
    }
}
