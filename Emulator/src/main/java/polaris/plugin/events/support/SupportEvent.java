package polaris.plugin.events.support;

import polaris.hotel.users.User;
import polaris.plugin.Event;

public abstract class SupportEvent extends Event {

    public User moderator;

    public SupportEvent(User moderator) {
        this.moderator = moderator;
    }
}
