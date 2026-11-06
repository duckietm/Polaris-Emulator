package polaris.messages.incoming.housekeeping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.permissions.Rank;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

final class HousekeepingTargetRankGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger(HousekeepingTargetRankGuard.class);

    private HousekeepingTargetRankGuard() {}

    static boolean canTargetUser(User operator, int targetUserId) {
        if (operator == null || targetUserId <= 0) {
            return false;
        }

        UserInfo targetInfo = Emulator.getGameEnvironment().getUserManager().getUserInfo(targetUserId);
        if (targetInfo == null) {
            return true;
        }

        int operatorRankId = operator.getUserInfo().getRank().getId();
        int targetRankId = targetInfo.getRank().getId();
        int highestConfiguredRankId = highestConfiguredRankId();
        HousekeepingRankPolicy.Decision decision =
                HousekeepingRankPolicy.evaluate(operatorRankId, targetRankId, highestConfiguredRankId);

        if (!decision.allowed()) {
            LOGGER.warn(
                    "Housekeeping target rejected: operatorUserId={}, targetUserId={}, operatorRankId={}, targetRankId={}, highestConfiguredRankId={}, reason={}",
                    operator.getUserInfo().getId(),
                    targetUserId,
                    operatorRankId,
                    targetRankId,
                    highestConfiguredRankId,
                    decision.reason());
        }

        return decision.allowed();
    }

    static boolean canTargetRank(User operator, int targetRankId) {
        if (operator == null || targetRankId <= 0) {
            return false;
        }

        int operatorRankId = operator.getUserInfo().getRank().getId();
        int highestConfiguredRankId = highestConfiguredRankId();
        HousekeepingRankPolicy.Decision decision =
                HousekeepingRankPolicy.evaluate(operatorRankId, targetRankId, highestConfiguredRankId);

        if (!decision.allowed()) {
            LOGGER.warn(
                    "Housekeeping rank rejected: operatorUserId={}, operatorRankId={}, targetRankId={}, highestConfiguredRankId={}, reason={}",
                    operator.getUserInfo().getId(),
                    operatorRankId,
                    targetRankId,
                    highestConfiguredRankId,
                    decision.reason());
        }

        return decision.allowed();
    }

    static boolean canAssignRank(User operator, int rankId) {
        return canTargetRank(operator, rankId);
    }

    /** The highest configured rank: the one the housekeeping lockdown still lets in. */
    static boolean isTopRank(User operator) {
        if (operator == null
                || operator.getUserInfo() == null
                || operator.getUserInfo().getRank() == null) {
            return false;
        }

        return operator.getUserInfo().getRank().getId() >= highestConfiguredRankId();
    }

    private static int highestConfiguredRankId() {
        int highestRankId = 0;
        for (Rank rank : Emulator.getGameEnvironment().getPermissionsManager().getAllRanks()) {
            highestRankId = Math.max(highestRankId, rank.getId());
        }

        return highestRankId;
    }
}
