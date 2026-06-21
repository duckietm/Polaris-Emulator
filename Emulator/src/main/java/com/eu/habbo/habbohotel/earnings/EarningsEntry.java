package com.eu.habbo.habbohotel.earnings;

import java.util.List;

public record EarningsEntry(EarningsCategory category, boolean enabled, boolean claimable, int nextClaimAt, List<EarningsReward> rewards) {
    public EarningsEntry {
        nextClaimAt = Math.max(0, nextClaimAt);
        rewards = List.copyOf(rewards);
    }
}
