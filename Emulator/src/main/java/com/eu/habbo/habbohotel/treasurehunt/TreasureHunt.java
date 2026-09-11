package com.eu.habbo.habbohotel.treasurehunt;

/**
 * One treasure hunt as configured in {@code treasure_hunts}. {@code totalSteps}
 * is the number of enabled rows the hunt has in {@code treasure_hunt_items}, so
 * the definition can never disagree with the items that are actually hidden.
 */
public record TreasureHunt(
        int id,
        String code,
        int requiredLevel,
        int requiredLevelPaying,
        String rewardBadge,
        int rewardItemId,
        int rewardPoints,
        int rewardPointsType,
        int totalSteps) {}
