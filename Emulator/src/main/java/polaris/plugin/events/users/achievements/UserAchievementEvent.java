package polaris.plugin.events.users.achievements;

import polaris.hotel.achievements.Achievement;
import polaris.hotel.users.User;
import polaris.plugin.events.users.UserEvent;

public abstract class UserAchievementEvent extends UserEvent {

    public final Achievement achievement;

    public UserAchievementEvent(User user, Achievement achievement) {
        super(user);

        this.achievement = achievement;
    }
}
