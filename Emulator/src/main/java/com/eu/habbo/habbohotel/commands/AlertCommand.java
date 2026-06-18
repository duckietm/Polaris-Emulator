package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class AlertCommand extends Command {

    public AlertCommand() {
        super("cmd_alert", Emulator.getTexts().getValue("commands.keys.cmd_alert").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (params.length < 2) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_alert.forgot_username"), RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (params.length < 3) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_alert.forgot_message"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        String targetUsername = params[1];
        StringBuilder message = new StringBuilder();

        for (int i = 2; i < params.length; i++) {
            message.append(params[i]).append(" ");
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetUsername);

        if (habbo != null) {
            if (!CommandTargetGuard.canTarget(gameClient.getHabbo(), habbo)) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            habbo.alert(message + "\r\n    -" + gameClient.getHabbo().getHabboInfo().getUsername());
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_alert.message_send").replace("%user%", targetUsername), RoomChatMessageBubbles.ALERT);
        } else {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_alert.user_offline").replace("%user%", targetUsername), RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}
