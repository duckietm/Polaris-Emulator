package com.eu.habbo.habbohotel.communitygoals;

/**
 * One prize of the official {@code CommunityGoalEarnedPrizes} packet (3319), in the
 * order the AIR {@code PrizeData} reads it. {@code rewardCode} is a badge code when
 * {@code badge} is true, otherwise the item name of the furni that is handed out.
 */
public record CommunityGoalPrize(
        int communityGoalId,
        String communityGoalCode,
        int userRank,
        String rewardCode,
        boolean badge,
        String localizedName) {}
