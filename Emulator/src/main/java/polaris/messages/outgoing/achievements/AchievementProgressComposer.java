package polaris.messages.outgoing.achievements;

import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementLevel;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AchievementProgressComposer extends MessageComposer {
    private final User user;
    private final Achievement achievement;

    public AchievementProgressComposer(User user, Achievement achievement) {
        this.user = user;
        this.achievement = achievement;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AchievementProgressComposer);

        appendAchievement(this.response, this.user, this.achievement);
        this.response.appendShort(this.achievement.state);

        return this.response;
    }

    static void appendAchievement(ServerMessage response, User user, Achievement achievement) {
        int progress = Math.max(0, user.getUserStats().getAchievementProgress(achievement));
        AchievementLevel currentLevel = achievement.getLevelForProgress(progress);
        AchievementLevel nextLevel = achievement.getNextLevel(currentLevel != null ? currentLevel.level : 0);
        AchievementLevel targetLevel = nextLevel != null ? nextLevel : currentLevel;
        int target = targetLevel != null ? targetLevel.level : 1;

        response.appendInt(achievement.id);
        response.appendInt(target);
        response.appendString("ACH_" + achievement.name + target);
        response.appendInt(currentLevel != null ? currentLevel.progress : 0);
        response.appendInt(targetLevel != null ? targetLevel.progress : 0);
        response.appendInt(nextLevel != null ? nextLevel.rewardAmount : 0);
        response.appendInt(nextLevel != null ? nextLevel.rewardType : 0);
        response.appendInt(progress);
        response.appendBoolean(AchievementManager.hasAchieved(user, achievement));
        response.appendString(achievement.category.name().toLowerCase(java.util.Locale.ROOT));
        response.appendString(achievement.subcategory);
        response.appendInt(achievement.levels.size());
        response.appendInt(achievement.displayMethod);
    }

    public User getUser() {
        return user;
    }

    public Achievement getAchievement() {
        return achievement;
    }
}
