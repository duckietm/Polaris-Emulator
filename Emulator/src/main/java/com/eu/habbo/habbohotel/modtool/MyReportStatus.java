package com.eu.habbo.habbohotel.modtool;

/**
 * One row of the official "my_reports" window (AIR 13 `MyReportStatus`): a report this
 * player filed, how it was decided, and where its appeal stands.
 *
 * <p>Times are epoch milliseconds because that is what the official reader expects;
 * {@code closeTime} and {@code appealResolutionTime} are {@code -1} while the report,
 * respectively the appeal, is still waiting for a decision.
 */
public record MyReportStatus(
        int id,
        long creationTime,
        String userMessage,
        int userCategory,
        String reportedAccountName,
        long closeTime,
        boolean sanctioned,
        boolean sanctionGivenByAutoModeration,
        int appealStatus,
        long appealCreationTime,
        long appealResolutionTime) {

    /** No appeal was ever filed. */
    public static final int APPEAL_NONE = 0;
    /** An appeal is filed and waiting for staff. */
    public static final int APPEAL_PENDING = 1;
    /** The appeal was reviewed and staff acted on it. */
    public static final int APPEAL_ACTION = 2;
    /** The appeal was reviewed and staff did not act. */
    public static final int APPEAL_NO_ACTION = 3;

    /**
     * The official client only enables the appeal button on a report that was decided,
     * ended without a sanction and has not been appealed yet
     * ({@code MyReportStatus.updateAppealButton}).
     */
    public boolean canBeAppealed() {
        return this.appealStatus == APPEAL_NONE && this.closeTime != -1 && !this.sanctioned;
    }
}
