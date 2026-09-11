package com.eu.habbo.habbohotel.guides;

public enum GuardianVoteType {
    FORWARDED(-1),
    WAITING(0),
    ACCEPTABLY(1),
    BADLY(2),
    AWFULLY(3),
    NOT_VOTED(4),
    SEARCHING(5);

    private final int type;

    GuardianVoteType(int type) {
        this.type = type;
    }

    public int getType() {
        return this.type;
    }

    /**
     * The wire code the official client expects in the two vote fields of
     * {@code ChatReviewSessionResults} (3276): {@code GuideSessionController.statusFromVote}
     * reads 0 = ok, 1 = bad, 2 = very bad and -1 = refused / inconclusive. Only the trailing
     * status array uses {@link #getType()}, which indexes STATUS_KEYS directly.
     */
    public int getVoteCode() {
        return switch (this) {
            case ACCEPTABLY -> 0;
            case BADLY -> 1;
            case AWFULLY -> 2;
            default -> -1;
        };
    }
}
