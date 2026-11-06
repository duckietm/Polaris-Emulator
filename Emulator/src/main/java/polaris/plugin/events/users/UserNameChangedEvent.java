package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserNameChangedEvent extends UserEvent {
    public final String oldName;

    public UserNameChangedEvent(User user, String oldName) {
        super(user);

        this.oldName = oldName;
    }
}
