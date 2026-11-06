package polaris.plugin.events.support;

import polaris.hotel.users.User;

public class SupportUserKickEvent extends SupportEvent {

    public User target;

    public String message;

    public SupportUserKickEvent(User moderator, User target, String message) {
        super(moderator);

        this.target = target;
        this.message = message;
    }
}
