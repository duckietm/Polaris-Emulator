package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HousekeepingTargetRankGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger(HousekeepingTargetRankGuard.class);

    private HousekeepingTargetRankGuard() {}

    static boolean canTargetUser(Habbo operator, int targetUserId) {
        if (operator == null || targetUserId <= 0) {
            return false;
        }

        HabboInfo targetInfo = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(targetUserId);
        if (targetInfo == null) {
            return true;
        }

        int operatorRankId = operator.getHabboInfo().getRank().getId();
        int targetRankId = targetInfo.getRank().getId();
        int highestConfiguredRankId = highestConfiguredRankId();
        HousekeepingRankPolicy.Decision decision =
                HousekeepingRankPolicy.evaluate(operatorRankId, targetRankId, highestConfiguredRankId);

        if (!decision.allowed()) {
            LOGGER.warn(
                    "Housekeeping target rejected: operatorUserId={}, targetUserId={}, operatorRankId={}, targetRankId={}, highestConfiguredRankId={}, reason={}",
                    operator.getHabboInfo().getId(),
                    targetUserId,
                    operatorRankId,
                    targetRankId,
                    highestConfiguredRankId,
                    decision.reason());
        }

        return decision.allowed();
    }

    static boolean canTargetRank(Habbo operator, int targetRankId) {
        if (operator == null || targetRankId <= 0) {
            return false;
        }

        int operatorRankId = operator.getHabboInfo().getRank().getId();
        int highestConfiguredRankId = highestConfiguredRankId();
        HousekeepingRankPolicy.Decision decision =
                HousekeepingRankPolicy.evaluate(operatorRankId, targetRankId, highestConfiguredRankId);

        if (!decision.allowed()) {
            LOGGER.warn(
                    "Housekeeping rank rejected: operatorUserId={}, operatorRankId={}, targetRankId={}, highestConfiguredRankId={}, reason={}",
                    operator.getHabboInfo().getId(),
                    operatorRankId,
                    targetRankId,
                    highestConfiguredRankId,
                    decision.reason());
        }

        return decision.allowed();
    }

    static boolean canAssignRank(Habbo operator, int rankId) {
        return canTargetRank(operator, rankId);
    }

    private static int highestConfiguredRankId() {
        int highestRankId = 0;
        for (Rank rank : Emulator.getGameEnvironment().getPermissionsManager().getAllRanks()) {
            highestRankId = Math.max(highestRankId, rank.getId());
        }

        return highestRankId;
    }
}
