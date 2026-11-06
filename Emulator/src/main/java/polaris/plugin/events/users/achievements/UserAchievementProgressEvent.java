package polaris.plugin.events.users.achievements;

import polaris.hotel.achievements.Achievement;
import polaris.hotel.users.User;

public class UserAchievementProgressEvent extends UserAchievementEvent {

    public final int progressed;

    public UserAchievementProgressEvent(User user, Achievement achievement, int progressed) {
        super(user, achievement);

        this.progressed = progressed;
    }
}
