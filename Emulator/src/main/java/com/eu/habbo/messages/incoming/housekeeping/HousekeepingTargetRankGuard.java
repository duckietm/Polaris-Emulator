package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;

final class HousekeepingTargetRankGuard {
    private HousekeepingTargetRankGuard() {
    }

    static boolean canTargetUser(Habbo operator, int targetUserId) {
        if (operator == null || targetUserId <= 0) {
            return false;
        }

        HabboInfo targetInfo = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(targetUserId);
        if (targetInfo == null) {
            return true;
        }

        return canTargetRank(operator, targetInfo.getRank().getId());
    }

    static boolean canTargetRank(Habbo operator, int targetRankId) {
        if (operator == null || targetRankId <= 0) {
            return false;
        }

        int operatorRankId = operator.getHabboInfo().getRank().getId();

        return targetRankId < operatorRankId || isCoreRank(operatorRankId) && targetRankId <= operatorRankId;
    }

    static boolean canAssignRank(Habbo operator, int rankId) {
        return canTargetRank(operator, rankId);
    }

    private static boolean isCoreRank(int rankId) {
        int highestRankId = 0;
        for (Rank rank : Emulator.getGameEnvironment().getPermissionsManager().getAllRanks()) {
            highestRankId = Math.max(highestRankId, rank.getId());
        }

        return highestRankId > 0 && rankId >= highestRankId;
    }
}
