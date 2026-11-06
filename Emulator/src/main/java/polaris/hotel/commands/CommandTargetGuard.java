package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.permissions.Rank;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

public final class CommandTargetGuard {
    static final String REFUSED_KEY = "commands.error.cmd_ban.target_rank_higher";

    private CommandTargetGuard() {}

    /**
     * For a command that acts on another user: answers true, after whispering the shared refusal,
     * when the issuer may not act on {@code target}. Acting on oneself is left to the command, and so
     * is a target that was not found, which keeps each command's own not-found answer.
     */
    public static boolean refuse(User issuer, User target) {
        return target != null && refuse(issuer, target.getUserInfo());
    }

    /** The same for a target that may be offline. */
    static boolean refuse(User issuer, UserInfo target) {
        if (target == null || issuer.getUserInfo().getId() == target.getId() || canTarget(issuer, target)) {
            return false;
        }

        issuer.whisperLocalized(REFUSED_KEY, RoomChatMessageBubbles.ALERT);
        return true;
    }

    public static boolean canTarget(User moderator, User target) {
        return target != null && canTarget(moderator, target.getUserInfo());
    }

    static boolean canTarget(User moderator, UserInfo target) {
        if (moderator == null || target == null || moderator.getUserInfo().getId() == target.getId()) {
            return false;
        }

        int moderatorRankId = moderator.getUserInfo().getRank().getId();
        int targetRankId = target.getRank().getId();

        return targetRankId < moderatorRankId || isCoreRank(moderatorRankId) && targetRankId <= moderatorRankId;
    }

    static boolean canAssignRank(User moderator, Rank rank) {
        if (moderator == null || rank == null) {
            return false;
        }

        int moderatorRankId = moderator.getUserInfo().getRank().getId();
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
