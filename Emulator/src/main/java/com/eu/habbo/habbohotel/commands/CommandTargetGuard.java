package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;

final class CommandTargetGuard {
    private CommandTargetGuard() {
    }

    static boolean canTarget(Habbo moderator, Habbo target) {
        return target != null && canTarget(moderator, target.getHabboInfo());
    }

    static boolean canTarget(Habbo moderator, HabboInfo target) {
        if (moderator == null || target == null || moderator.getHabboInfo().getId() == target.getId()) {
            return false;
        }

        int moderatorRankId = moderator.getHabboInfo().getRank().getId();
        int targetRankId = target.getRank().getId();

        return targetRankId < moderatorRankId || isCoreRank(moderatorRankId) && targetRankId <= moderatorRankId;
    }

    static boolean canAssignRank(Habbo moderator, Rank rank) {
        if (moderator == null || rank == null) {
            return false;
        }

        int moderatorRankId = moderator.getHabboInfo().getRank().getId();
        int targetRankId = rank.getId();

        return targetRankId < moderatorRankId || isCoreRank(moderatorRankId) && targetRankId <= moderatorRankId;
    }

    private static boolean isCoreRank(int rankId) {
        int highestRankId = Emulator.getGameEnvironment().getPermissionsManager().getAllRanks().stream()
                .mapToInt(Rank::getId)
                .max()
                .orElse(0);

        return highestRankId > 0 && rankId >= highestRankId;
    }
}
