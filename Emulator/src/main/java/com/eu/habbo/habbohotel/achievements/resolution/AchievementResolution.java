package com.eu.habbo.habbohotel.achievements.resolution;

/**
 * The promise a resolution furni carries: whose it is, which achievement was picked, the level that
 * has to be reached and the badge that stands for it. {@code completedAt} is zero while it is open.
 */
public record AchievementResolution(
        int itemId,
        int userId,
        int achievementId,
        int targetLevel,
        String badgeCode,
        int startedAt,
        int endsAt,
        int completedAt) {

    public boolean completed() {
        return this.completedAt > 0;
    }

    public boolean expired(int now) {
        return this.completedAt == 0 && this.endsAt > 0 && now >= this.endsAt;
    }

    /** Seconds the owner has left, never negative; the window counts down from it. */
    public int secondsLeft(int now) {
        return Math.max(0, this.endsAt - now);
    }
}
