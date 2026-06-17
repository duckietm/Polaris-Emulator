package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketState;
import com.eu.habbo.habbohotel.users.Habbo;

final class ModToolTicketGuard {
    static final int MAX_RELEASE_BATCH = 50;

    private ModToolTicketGuard() {
    }

    static boolean isPositiveId(int id) {
        return id > 0;
    }

    static boolean isValidReleaseBatch(int count) {
        return count > 0 && count <= MAX_RELEASE_BATCH;
    }

    static boolean isOwnedBy(ModToolIssue issue, Habbo moderator) {
        return issue != null && moderator != null && issue.modId == moderator.getHabboInfo().getId();
    }

    static boolean canPick(ModToolIssue issue) {
        return issue != null && issue.state != ModToolTicketState.PICKED;
    }
}
