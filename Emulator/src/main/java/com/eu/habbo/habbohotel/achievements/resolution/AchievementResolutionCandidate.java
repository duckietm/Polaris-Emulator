package com.eu.habbo.habbohotel.achievements.resolution;

/**
 * One row of the picker: the achievement, the level the owner is on, the badge of the level they
 * would have to reach, that level, and why they may not pick it.
 */
public record AchievementResolutionCandidate(
        int achievementId, int level, String badgeCode, int requiredLevel, int state) {

    /** The client reads these out of `resolution.disabled.<state>`; zero means it can be picked. */
    public static final int ENABLED = 0;

    public static final int ALL_LEVELS_DONE = 1;

    public static final int ALREADY_PROMISED = 2;
}
