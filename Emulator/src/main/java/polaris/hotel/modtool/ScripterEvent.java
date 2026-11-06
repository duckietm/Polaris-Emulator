package polaris.hotel.modtool;

import polaris.hotel.users.User;
import polaris.plugin.events.support.SupportEvent;

public class ScripterEvent extends SupportEvent {
    public final User user;
    public final String reason;

    public ScripterEvent(User user, String reason) {
        super(null);

        this.user = user;
        this.reason = reason;
    }
}
