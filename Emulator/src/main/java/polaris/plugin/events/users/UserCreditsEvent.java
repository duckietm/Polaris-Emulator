package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserCreditsEvent extends UserEvent {

    public int credits;

    public UserCreditsEvent(User user, int credits) {
        super(user);

        this.credits = credits;
    }
}
