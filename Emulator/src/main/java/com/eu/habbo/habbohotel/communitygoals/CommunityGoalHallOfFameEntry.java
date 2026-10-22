package com.eu.habbo.habbohotel.communitygoals;

/**
 * One player of the official {@code CommunityGoalHallOfFame} packet (3005), in the
 * order the AIR {@code HallOfFameEntryData} reads it: user id, name, figure, rank
 * and the score the player reached in the goal.
 */
public record CommunityGoalHallOfFameEntry(int userId, String userName, String figure, int rank, int currentScore) {}
