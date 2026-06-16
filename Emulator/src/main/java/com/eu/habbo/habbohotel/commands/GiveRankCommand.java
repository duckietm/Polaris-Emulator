package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import org.apache.commons.lang3.StringUtils;

public class GiveRankCommand extends Command {
    public GiveRankCommand() {
        super("cmd_give_rank", Emulator.getTexts().getValue("commands.keys.cmd_give_rank").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Rank rank = null;
        if (params.length == 1) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_give_rank.missing_username") + Emulator.getTexts().getValue("commands.description.cmd_give_rank"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length == 2) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_give_rank.missing_rank") + Emulator.getTexts().getValue("commands.description.cmd_give_rank"), RoomChatMessageBubbles.ALERT);
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
                if (!CommandTargetGuard.canAssignRank(gameClient.getHabbo(), rank)) {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_give_rank.higher").replace("%username%", params[1]).replace("%id%", rank.getName()), RoomChatMessageBubbles.ALERT);
                    return true;
                }

                HabboInfo habbo = HabboManager.getOfflineHabboInfo(params[1]);

                if (habbo != null) {
                    if (!CommandTargetGuard.canTarget(gameClient.getHabbo(), habbo)) {
                        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_give_rank.higher.other").replace("%username%", params[1]).replace("%id%", rank.getName()), RoomChatMessageBubbles.ALERT);
                        return true;
                    }

                    Emulator.getGameEnvironment().getHabboManager().setRank(habbo.getId(), rank.getId());

                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_give_rank.updated").replace("%id%", rank.getName()).replace("%username%", params[1]), RoomChatMessageBubbles.ALERT);
                    return true;
                } else {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_give_rank.user_offline").replace("%id%", rank.getName()).replace("%username%", params[1]), RoomChatMessageBubbles.ALERT);
                    return true;
                }
            }
        }

        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.errors.cmd_give_rank.not_found").replace("%id%", params[2]).replace("%username%", params[1]), RoomChatMessageBubbles.ALERT);
        return true;
    }
}
