package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;

public class MuteCommand extends Command {
    public MuteCommand() {
        super("cmd_mute", Emulator.getTexts().getValue("commands.keys.cmd_mute").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 1) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_mute.not_specified"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);

        if (habbo == null) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_mute.not_found").replace("%user%", params[1]), RoomChatMessageBubbles.ALERT);
            return true;
        } else {
            if (habbo == gameClient.getHabbo()) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_mute.self"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (!CommandTargetGuard.canTarget(gameClient.getHabbo(), habbo)) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            int duration = Integer.MAX_VALUE;

            if (params.length == 3) {
                try {
                    duration = Integer.parseInt(params[2]);

                    if (duration <= 0)
                        throw new Exception("");
                } catch (Exception e) {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_mute.time"), RoomChatMessageBubbles.ALERT);
                    return true;
                }
            }

            habbo.mute(duration, false);

            if (habbo.getHabboInfo().getCurrentRoom() != null) {
                habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserIgnoredComposer(habbo, RoomUserIgnoredComposer.MUTED).compose()); //: RoomUserIgnoredComposer.UNIGNORED
            }

            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_mute.muted").replace("%user%", params[1]), RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
