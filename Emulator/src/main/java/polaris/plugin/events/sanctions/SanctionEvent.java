package polaris.plugin.events.sanctions;

import polaris.hotel.users.User;
import polaris.plugin.events.support.SupportEvent;

public class SanctionEvent extends SupportEvent {
    public User target;

    public int sanctionLevel;

    public SanctionEvent(User moderator, User target, int sanctionLevel) {
        super(moderator);

        this.target = target;
        this.sanctionLevel = sanctionLevel;
    }
}
