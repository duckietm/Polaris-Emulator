package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.modtool.ModToolBanType;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;

public class SuperbanCommand extends Command {
    public SuperbanCommand() {
        super("cmd_super_ban", Emulator.getTexts().getValue("commands.keys.cmd_super_ban").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        HabboInfo habbo = null;
        StringBuilder reason = new StringBuilder();
        if (params.length >= 2) {
            Habbo h = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);

            if (h != null) {
                habbo = h.getHabboInfo();
            } else {
                habbo = HabboManager.getOfflineHabboInfo(params[1]);
            }
        }

        if (params.length > 2) {
            for (int i = 2; i < params.length; i++) {
                reason.append(params[i]);
                reason.append(" ");
            }
        }

        int count;
        if (habbo != null) {
            if (habbo == gameClient.getHabbo().getHabboInfo()) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_super_ban.ban_self"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (!CommandTargetGuard.canTarget(gameClient.getHabbo(), habbo)) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            count = Emulator.getGameEnvironment().getModToolManager().ban(habbo.getId(), gameClient.getHabbo(), reason.toString(), IPBanCommand.TEN_YEARS, ModToolBanType.SUPER, -1).size();
        } else {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.user_offline"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_super_ban").replace("%count%", count + ""), RoomChatMessageBubbles.ALERT);

        return true;
    }
}
