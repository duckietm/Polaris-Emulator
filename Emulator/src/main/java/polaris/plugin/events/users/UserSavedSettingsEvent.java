package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserSavedSettingsEvent extends UserEvent {

    public UserSavedSettingsEvent(User user) {
        super(user);
    }
}
