package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.modtool.ModToolBan;
import com.eu.habbo.habbohotel.modtool.ModToolBanType;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;

import java.util.List;

public class BanCommand extends Command {
    public BanCommand() {
        super("cmd_ban", Emulator.getTexts().getValue("commands.keys.cmd_ban").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.forgot_user"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length < 3) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.forgot_time"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        int banTime;
        try {
            banTime = Integer.parseInt(params[2]);
        } catch (Exception e) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.invalid_time"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (banTime < 600) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.time_to_short"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params[1].equalsIgnoreCase(gameClient.getHabbo().getHabboInfo().getUsername())) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.ban_self"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo t = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);

        HabboInfo target;
        if (t != null) {
            target = t.getHabboInfo();
        } else {
            target = HabboManager.getOfflineHabboInfo(params[1]);
        }

        if (target == null) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.user_offline"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!CommandTargetGuard.canTarget(gameClient.getHabbo(), target)) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"), RoomChatMessageBubbles.ALERT);
            return true;
        }


        StringBuilder reason = new StringBuilder();

        if (params.length > 3) {
            for (int i = 3; i < params.length; i++) {
                reason.append(params[i]).append(" ");
            }
        }

        List<ModToolBan> bans = Emulator.getGameEnvironment().getModToolManager().ban(target.getId(), gameClient.getHabbo(), reason.toString(), banTime, ModToolBanType.ACCOUNT, -1);
        if (bans == null || bans.isEmpty()) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.user_offline"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        ModToolBan ban = bans.get(0);
        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_ban.ban_issued").replace("%user%", target.getUsername()).replace("%time%", ban.expireDate - Emulator.getIntUnixTimestamp() + "").replace("%reason%", ban.reason), RoomChatMessageBubbles.ALERT);

        return true;
    }
}
