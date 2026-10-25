package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;

public final class CommandTargetGuard {
    static final String REFUSED_KEY = "commands.error.cmd_ban.target_rank_higher";

    private CommandTargetGuard() {}

    /**
     * For a command that acts on another user: answers true, after whispering the shared refusal,
     * when the issuer may not act on {@code target}. Acting on oneself is left to the command, and so
     * is a target that was not found, which keeps each command's own not-found answer.
     */
    public static boolean refuse(Habbo issuer, Habbo target) {
        return target != null && refuse(issuer, target.getHabboInfo());
    }

    /** The same for a target that may be offline. */
    static boolean refuse(Habbo issuer, HabboInfo target) {
        if (target == null || issuer.getHabboInfo().getId() == target.getId() || canTarget(issuer, target)) {
            return false;
        }

        issuer.whisperLocalized(REFUSED_KEY, RoomChatMessageBubbles.ALERT);
        return true;
    }

    public static boolean canTarget(Habbo moderator, Habbo target) {
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
