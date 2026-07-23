package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarAvatarObject;
import com.eu.habbo.habbohotel.users.Habbo;

/**
 * A participant of one SnowWar match.
 */
public class SnowWarGamePlayer {

    private final Habbo habbo;
    private final int userId;
    private final int objectId;
    private final int teamId;

    private volatile SnowWarAvatarObject avatar;
    private volatile boolean stageReady = false;
    private volatile boolean playAgain = false;

    public SnowWarGamePlayer(Habbo habbo, int objectId, int teamId) {
        this.habbo = habbo;
        this.userId = habbo.getHabboInfo().getId();
        this.objectId = objectId;
        this.teamId = teamId;
    }

    public Habbo getHabbo() {
        return this.habbo;
    }

    public int getUserId() {
        return this.userId;
    }

    public int getObjectId() {
        return this.objectId;
    }

    public int getTeamId() {
        return this.teamId;
    }

    public SnowWarAvatarObject getAvatar() {
        return this.avatar;
    }

    public void setAvatar(SnowWarAvatarObject avatar) {
        this.avatar = avatar;
    }

    public boolean isStageReady() {
        return this.stageReady;
    }

    public void setStageReady(boolean stageReady) {
        this.stageReady = stageReady;
    }

    public boolean isPlayAgain() {
        return this.playAgain;
    }

    public void setPlayAgain(boolean playAgain) {
        this.playAgain = playAgain;
    }

    public SnowWarAttributes getAttributes() {
        return SnowWarPlayers.get(this.userId);
    }
}
