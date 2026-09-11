package com.eu.habbo.habbohotel.communitygoals;

/**
 * One community goal of {@code community_goals}. {@code levelThresholds} are the
 * total scores that raise the community level and {@code rewardUserLimits} the
 * {@code rewardUserLimits} array of the official {@code CommunityGoalProgress}
 * packet: how many players each level rewards.
 */
public record CommunityGoal(
        int id,
        String code,
        int totalScore,
        int[] levelThresholds,
        int[] rewardUserLimits,
        int[] voteOptions,
        int expiresAt) {

    /** The highest level the community reached with {@code totalScore}. */
    public int highestAchievedLevel() {
        int level = 0;

        for (int threshold : this.levelThresholds) {
            if (this.totalScore >= threshold) level++;
        }

        return level;
    }

    /** The score still missing for the next level, or 0 once every level is done. */
    public int scoreRemainingUntilNextLevel() {
        int level = this.highestAchievedLevel();

        if (level >= this.levelThresholds.length) return 0;

        return Math.max(0, this.levelThresholds[level] - this.totalScore);
    }

    /** How far the community walked from the previous level to the next one, 0..100. */
    public int percentCompletionTowardsNextLevel() {
        int level = this.highestAchievedLevel();

        if (level >= this.levelThresholds.length) return 100;

        int previous = level == 0 ? 0 : this.levelThresholds[level - 1];
        int span = this.levelThresholds[level] - previous;

        if (span <= 0) return 100;

        return Math.max(0, Math.min(100, ((this.totalScore - previous) * 100) / span));
    }

    public boolean hasVoteOption(int optionId) {
        for (int option : this.voteOptions) {
            if (option == optionId) return true;
        }

        return false;
    }
}
