package com.eu.habbo.habbohotel.earnings;

import java.util.List;

public class EarningsEntry {
    private final EarningsCategory category;
    private final boolean enabled;
    private final boolean claimable;
    private final int nextClaimAt;
    private final List<EarningsReward> rewards;

    public EarningsEntry(EarningsCategory category, boolean enabled, boolean claimable, int nextClaimAt, List<EarningsReward> rewards) {
        this.category = category;
        this.enabled = enabled;
        this.claimable = claimable;
        this.nextClaimAt = Math.max(0, nextClaimAt);
        this.rewards = List.copyOf(rewards);
    }

    public EarningsCategory getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isClaimable() {
        return claimable;
    }

    public int getNextClaimAt() {
        return nextClaimAt;
    }

    public List<EarningsReward> getRewards() {
        return rewards;
    }
}
