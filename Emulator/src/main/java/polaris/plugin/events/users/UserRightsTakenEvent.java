package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserRightsTakenEvent extends UserEvent {
    public final int victimId;
    public final User victim;

    public UserRightsTakenEvent(User user, int victimId, User victim) {
        super(user);

        this.victimId = victimId;
        this.victim = victim;
    }
}
