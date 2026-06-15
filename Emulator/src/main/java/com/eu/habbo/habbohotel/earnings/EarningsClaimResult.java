package com.eu.habbo.habbohotel.earnings;

public class EarningsClaimResult {
    public enum Status {
        SUCCESS,
        DISABLED,
        UNKNOWN_CATEGORY,
        ALREADY_CLAIMED,
        NO_REWARD,
        ERROR
    }

    private final EarningsCategory category;
    private final Status status;
    private final EarningsEntry entry;

    public EarningsClaimResult(EarningsCategory category, Status status, EarningsEntry entry) {
        this.category = category;
        this.status = status;
        this.entry = entry;
    }

    public EarningsCategory getCategory() {
        return category;
    }

    public String getCategoryKey() {
        return category == null ? "" : category.getKey();
    }

    public Status getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public EarningsEntry getEntry() {
        return entry;
    }
}
