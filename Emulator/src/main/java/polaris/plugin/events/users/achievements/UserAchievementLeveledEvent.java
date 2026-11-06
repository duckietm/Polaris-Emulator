package polaris.plugin.events.users.achievements;

import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementLevel;
import polaris.hotel.users.User;

public class UserAchievementLeveledEvent extends UserAchievementEvent {

    public final AchievementLevel oldLevel;

    public final AchievementLevel newLevel;

    public UserAchievementLeveledEvent(
            User user, Achievement achievement, AchievementLevel oldLevel, AchievementLevel newLevel) {
        super(user, achievement);

        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }
}
