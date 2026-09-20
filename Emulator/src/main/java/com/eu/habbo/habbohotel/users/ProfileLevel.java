package com.eu.habbo.habbohotel.users;

/**
 * The level shown on the extended profile, derived from the achievement score.
 *
 * <p>Polaris has no account-wide level of its own: the only levels are a rank's, a pet's and the
 * one the wired level-up add-on derives from a variable. The profile needs one number that grows
 * with what the user has done, and the achievement score is the only account-wide measure that
 * does. Level {@code L} starts at {@code 50 * L * (L - 1)} points, so the first levels come quickly
 * and each one then asks a little more than the last: 0, 100, 300, 600, 1000, 1500...
 */
public final class ProfileLevel {
    private static final int STEP = 50;

    private final int level;
    private final int levelStart;
    private final int nextLevelStart;

    private ProfileLevel(int level, int levelStart, int nextLevelStart) {
        this.level = level;
        this.levelStart = levelStart;
        this.nextLevelStart = nextLevelStart;
    }

    /** Points needed to reach {@code level}; level 1 starts at zero. */
    public static int startOf(int level) {
        if (level <= 1) return 0;
        return STEP * level * (level - 1);
    }

    /** The level a user with {@code achievementScore} points has reached, never below 1. */
    public static ProfileLevel forScore(int achievementScore) {
        int score = Math.max(0, achievementScore);
        int level = 1;
        while (startOf(level + 1) <= score) {
            level++;
        }
        return new ProfileLevel(level, startOf(level), startOf(level + 1));
    }

    public int getLevel() {
        return this.level;
    }

    public int getLevelStart() {
        return this.levelStart;
    }

    public int getNextLevelStart() {
        return this.nextLevelStart;
    }
}
