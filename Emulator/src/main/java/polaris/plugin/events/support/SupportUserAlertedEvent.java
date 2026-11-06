package polaris.plugin.events.support;

import polaris.hotel.users.User;

public class SupportUserAlertedEvent extends SupportEvent {

    public User target;

    public String message;

    public SupportUserAlertedReason reason;

    public SupportUserAlertedEvent(User moderator, User target, String message, SupportUserAlertedReason reason) {
        super(moderator);

        this.message = message;
        this.target = target;
        this.reason = reason;
    }
}
