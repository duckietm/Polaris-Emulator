package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class UnmuteCommand extends Command {
    public UnmuteCommand() {
        super("cmd_unmute", Emulator.getTexts().getValue("commands.keys.cmd_unmute").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 1) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_unmute.not_specified"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);

        if (habbo == null) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_unmute.not_found").replace("%user%", params[1]), RoomChatMessageBubbles.ALERT);
            return true;
        } else {
            if (!CommandTargetGuard.canTarget(gameClient.getHabbo(), habbo)) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (!habbo.getHabboStats().allowTalk() || (habbo.getHabboInfo().getCurrentRoom() != null && habbo.getHabboInfo().getCurrentRoom().isMuted(habbo))) {
                if (!habbo.getHabboStats().allowTalk()) {
                    habbo.unMute();
                }

                if (habbo.getHabboInfo().getCurrentRoom() != null && habbo.getHabboInfo().getCurrentRoom().isMuted(habbo)) {
                    habbo.getHabboInfo().getCurrentRoom().unmuteHabbo(habbo);
                }

                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_unmute").replace("%user%", params[1]), RoomChatMessageBubbles.ALERT);
            } else {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_unmute.not_muted").replace("%user%", params[1]), RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        return true;
    }
}
