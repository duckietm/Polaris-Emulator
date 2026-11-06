package polaris.messages.incoming.modtool;

import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolTicketState;
import polaris.hotel.users.User;

final class ModToolTicketGuard {
    static final int MAX_RELEASE_BATCH = 50;

    private ModToolTicketGuard() {}

    static boolean isPositiveId(int id) {
        return id > 0;
    }

    static boolean isValidReleaseBatch(int count) {
        return count > 0 && count <= MAX_RELEASE_BATCH;
    }

    static boolean isValidIdListSize(int count) {
        return count >= 0 && count <= MAX_RELEASE_BATCH;
    }

    static boolean isOwnedBy(ModToolIssue issue, User moderator) {
        return issue != null
                && moderator != null
                && issue.modId == moderator.getUserInfo().getId();
    }

    static boolean canPick(ModToolIssue issue) {
        return issue != null && issue.state != ModToolTicketState.PICKED;
    }
}
