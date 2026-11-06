package polaris.hotel.games.snowwar;

import polaris.hotel.games.snowwar.objects.SnowWarAvatarObject;
import polaris.hotel.users.User;

/**
 * A participant of one SnowWar match.
 */
public class SnowWarGamePlayer {

    private final User user;
    private final int userId;
    private final int objectId;
    private final int teamId;

    private volatile SnowWarAvatarObject avatar;
    private volatile boolean stageReady = false;
    private volatile boolean playAgain = false;
    private volatile long lastActionTime = System.currentTimeMillis();
    private volatile boolean idleWarned = false;

    public SnowWarGamePlayer(User user, int objectId, int teamId) {
        this.user = user;
        this.userId = user.getUserInfo().getId();
        this.objectId = objectId;
        this.teamId = teamId;
    }

    public User getUser() {
        return this.user;
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

    public long getLastActionTime() {
        return this.lastActionTime;
    }

    public boolean isIdleWarned() {
        return this.idleWarned;
    }

    public void setIdleWarned(boolean idleWarned) {
        this.idleWarned = idleWarned;
    }

    /** Any input from the player (walk, throw, make a snowball, chat) counts as activity. */
    public void markActive(long now) {
        this.lastActionTime = now;
        this.idleWarned = false;
    }

    public SnowWarAttributes getAttributes() {
        return SnowWarPlayers.get(this.userId);
    }
}
