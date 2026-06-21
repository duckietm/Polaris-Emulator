package com.eu.habbo.habbohotel.earnings;

public record EarningsClaimResult(EarningsCategory category, Status status, EarningsEntry entry) {
    public enum Status {
        SUCCESS,
        DISABLED,
        UNKNOWN_CATEGORY,
        ALREADY_CLAIMED,
        NO_REWARD,
        ERROR
    }

    public String getCategoryKey() {
        return category == null ? "" : category.getKey();
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
