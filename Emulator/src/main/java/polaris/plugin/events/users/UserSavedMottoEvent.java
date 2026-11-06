package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserSavedMottoEvent extends UserEvent {
    public final String oldMotto;
    public String newMotto;

    public UserSavedMottoEvent(User user, String oldMotto, String newMotto) {
        super(user);
        this.oldMotto = oldMotto;
        this.newMotto = newMotto;
    }
}
