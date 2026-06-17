package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;

public class ProgressAchievement extends RCONMessage<ProgressAchievement.ProgressAchievementJSON> {
    static final int DEFAULT_MAX_PROGRESS = 10_000;

    public ProgressAchievement() {
        super(ProgressAchievementJSON.class);
    }

    @Override
    public void handle(Gson gson, ProgressAchievementJSON json) {
        int maxProgress = parseMaxProgress(Emulator.getConfig().getValue("rcon.achievement.max_progress", String.valueOf(DEFAULT_MAX_PROGRESS)));
        if (json.progress > maxProgress) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = "progress must be between 1 and " + maxProgress;
            return;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(json.user_id);

        if (habbo != null) {
            Achievement achievement = Emulator.getGameEnvironment().getAchievementManager().getAchievement(json.achievement_id);
            if (achievement != null) {
                AchievementManager.progressAchievement(habbo, achievement, json.progress);
            } else {
                this.status = RCONMessage.STATUS_ERROR;
            }
        } else {
            this.status = RCONMessage.HABBO_NOT_FOUND;
        }
    }

    static int parseMaxProgress(String configured) {
        try {
            int parsed = Integer.parseInt(configured);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }

        return DEFAULT_MAX_PROGRESS;
    }

    static class ProgressAchievementJSON {

        @Positive(message = "invalid user")
        public int user_id;

        @Positive(message = "invalid achievement")
        public int achievement_id;

        @Positive(message = "invalid progress")
        public int progress;
    }
}
