package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class DisconnectCommand extends Command {
    public DisconnectCommand() {
        super("cmd_disconnect", Emulator.getTexts().getValue("commands.keys.cmd_disconnect").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_disconnect.forgot_username"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params[1].equalsIgnoreCase(gameClient.getHabbo().getHabboInfo().getUsername())) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_disconnect.disconnect_self"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);

        if (target == null) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_disconnect.user_offline"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!CommandTargetGuard.canTarget(gameClient.getHabbo(), target)) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_disconnect.higher_rank"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        target.getClient().getChannel().close();

        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_disconnect.disconnected").replace("%user%", params[1]), RoomChatMessageBubbles.ALERT);
        return true;
    }
}
