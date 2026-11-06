package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserPointsEvent extends UserEvent {

    public int points;

    public int type;

    public UserPointsEvent(User user, int points, int type) {
        super(user);

        this.points = points;
        this.type = type;
    }
}
