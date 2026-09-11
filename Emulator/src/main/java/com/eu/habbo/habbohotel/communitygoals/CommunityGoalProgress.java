package com.eu.habbo.habbohotel.communitygoals;

/**
 * The payload of the official {@code CommunityGoalProgress} packet (2525), in the
 * order the AIR 13 parser reads it.
 */
public record CommunityGoalProgress(
        boolean hasGoalExpired,
        int personalContributionScore,
        int personalContributionRank,
        int communityTotalScore,
        int communityHighestAchievedLevel,
        int scoreRemainingUntilNextLevel,
        int percentCompletionTowardsNextLevel,
        String goalCode,
        int timeRemainingInSeconds,
        int[] rewardUserLimits) {}
