package polaris.plugin.events.users;

import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;

public class UserSavedLookEvent extends UserEvent {
    public UserGender gender;
    public String newLook;

    public UserSavedLookEvent(User user, UserGender gender, String newLook) {
        super(user);
        this.gender = gender;
        this.newLook = newLook;
    }
}
