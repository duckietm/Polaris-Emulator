package com.eu.habbo.messages.outgoing.achievements;

import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementLevel;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class AchievementProgressComposer extends MessageComposer {
    private final Habbo habbo;
    private final Achievement achievement;

    public AchievementProgressComposer(Habbo habbo, Achievement achievement) {
        this.habbo = habbo;
        this.achievement = achievement;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AchievementProgressComposer);

        appendAchievement(this.response, this.habbo, this.achievement);
        this.response.appendShort(this.achievement.state);

        return this.response;
    }

    static void appendAchievement(ServerMessage response, Habbo habbo, Achievement achievement) {
        int progress = Math.max(0, habbo.getHabboStats().getAchievementProgress(achievement));
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
        response.appendBoolean(AchievementManager.hasAchieved(habbo, achievement));
        response.appendString(achievement.category.name().toLowerCase(java.util.Locale.ROOT));
        response.appendString(achievement.subcategory);
        response.appendInt(achievement.levels.size());
        response.appendInt(achievement.displayMethod);
    }

    public Habbo getHabbo() {
        return habbo;
    }

    public Achievement getAchievement() {
        return achievement;
    }
}
