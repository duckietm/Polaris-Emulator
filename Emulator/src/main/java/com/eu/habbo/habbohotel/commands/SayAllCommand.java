package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class SayAllCommand extends Command {
    public SayAllCommand() {
        super("cmd_say_all", Emulator.getTexts().getValue("commands.keys.cmd_say_all").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_say_all.forgot_message"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        var message = new StringBuilder();
        for (int i = 1; i < params.length; i++) {
            message.append(params[i]).append(" ");
        }

        for (Habbo habbo : gameClient.getHabbo().getHabboInfo().getCurrentRoom().getHabbos()) {
            habbo.talk(message.toString());
        }

        return true;
    }
}
