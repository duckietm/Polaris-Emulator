package com.eu.habbo.messages.outgoing.events.resolution;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class NewYearResolutionProgressComposer extends MessageComposer {
    private final int stuffId;
    private final int achievementId;
    private final String achievementName;
    private final int currentProgress;
    private final int progressNeeded;
    private final int timeLeft;

    public NewYearResolutionProgressComposer(
            int stuffId,
            int achievementId,
            String achievementName,
            int currentProgress,
            int progressNeeded,
            int timeLeft) {
        this.stuffId = stuffId;
        this.achievementId = achievementId;
        this.achievementName = achievementName;
        this.currentProgress = currentProgress;
        this.progressNeeded = progressNeeded;
        this.timeLeft = timeLeft;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewYearResolutionProgressComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.stuffId));
        this.response.appendInt(this.achievementId);
        this.response.appendString(this.achievementName);
        this.response.appendInt(this.currentProgress);
        this.response.appendInt(this.progressNeeded);
        this.response.appendInt(this.timeLeft);
        return this.response;
    }

    public int getStuffId() {
        return stuffId;
    }

    public int getAchievementId() {
        return achievementId;
    }

    public String getAchievementName() {
        return achievementName;
    }

    public int getCurrentProgress() {
        return currentProgress;
    }

    public int getProgressNeeded() {
        return progressNeeded;
    }

    public int getTimeLeft() {
        return timeLeft;
    }
}
