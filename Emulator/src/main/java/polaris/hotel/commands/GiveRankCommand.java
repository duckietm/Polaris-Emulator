package polaris.hotel.commands;

import org.apache.commons.lang3.StringUtils;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.permissions.Rank;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;

public class GiveRankCommand extends Command {
    public GiveRankCommand() {
        super(
                "cmd_give_rank",
                Emulator.getTexts().getValue("commands.keys.cmd_give_rank").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Rank rank = null;
        if (params.length == 1) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_give_rank.missing_username")
                                    + Emulator.getTexts().getValue("commands.description.cmd_give_rank"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length == 2) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_give_rank.missing_rank")
                                    + Emulator.getTexts().getValue("commands.description.cmd_give_rank"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length == 3) {
            if (StringUtils.isNumeric(params[2])) {
                int rankId = Integer.parseInt(params[2]);
                if (Emulator.getGameEnvironment().getPermissionsManager().rankExists(rankId))
                    rank = Emulator.getGameEnvironment().getPermissionsManager().getRank(rankId);
            } else {
                rank = Emulator.getGameEnvironment().getPermissionsManager().getRankByName(params[2]);
            }

            if (rank != null) {
                if (!CommandTargetGuard.canAssignRank(gameClient.getUser(), rank)) {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.error.cmd_give_rank.higher")
                                            .replace("%username%", params[1])
                                            .replace("%id%", rank.getName()),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }

                UserInfo user = UserManager.getOfflineUserInfo(params[1]);

                if (user != null) {
                    if (!CommandTargetGuard.canTarget(gameClient.getUser(), user)) {
                        gameClient
                                .getUser()
                                .whisper(
                                        Emulator.getTexts()
                                                .getValue("commands.error.cmd_give_rank.higher.other")
                                                .replace("%username%", params[1])
                                                .replace("%id%", rank.getName()),
                                        RoomChatMessageBubbles.ALERT);
                        return true;
                    }

                    int fromRankId = user.getRank() != null ? user.getRank().getId() : 0;

                    Emulator.getGameEnvironment()
                            .getUserManager()
                            .setRank(
                                    user.getId(),
                                    rank.getId(),
                                    gameClient.getUser().getUserInfo().getId(),
                                    gameClient.getUser().getUserInfo().getUsername(),
                                    "give_rank");

                    HousekeepingAuditLog.log(
                            gameClient.getUser().getUserInfo().getId(),
                            gameClient.getUser().getUserInfo().getUsername(),
                            "user.set_rank",
                            user.getId(),
                            "fromRankId=" + fromRankId + " rankId=" + rank.getId() + " via=give_rank",
                            gameClient.getUser().getUserInfo().getIpLogin());

                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.succes.cmd_give_rank.updated")
                                            .replace("%id%", rank.getName())
                                            .replace("%username%", params[1]),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                } else {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.error.cmd_give_rank.user_offline")
                                            .replace("%id%", rank.getName())
                                            .replace("%username%", params[1]),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }
            }
        }

        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts()
                                .getValue("commands.errors.cmd_give_rank.not_found")
                                .replace("%id%", params[2])
                                .replace("%username%", params[1]),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
