package com.eu.habbo.messages.outgoing.achievements;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class AchievementListComposer extends MessageComposer {

    private final Habbo habbo;

    public AchievementListComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AchievementListComposer);

        var achievements = Emulator.getGameEnvironment().getAchievementManager().getAchievements().values().stream()
                .sorted(java.util.Comparator.comparingInt(achievement -> achievement.id))
                .toList();
        this.response.appendInt(achievements.size());
        for (Achievement achievement : achievements) {
            AchievementProgressComposer.appendAchievement(this.response, this.habbo, achievement);
        }
        this.response.appendString("");

        // Keep legacy repeated records intact; state metadata is a whole-packet extension.
        this.response.appendInt(achievements.size());
        for (Achievement achievement : achievements) {
            this.response.appendInt(achievement.id);
            this.response.appendShort(achievement.state);
        }

        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
