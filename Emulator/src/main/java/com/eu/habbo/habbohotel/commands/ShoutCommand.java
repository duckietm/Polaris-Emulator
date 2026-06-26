package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserShoutComposer;

public class ShoutCommand extends Command {
    public ShoutCommand() {
        super("cmd_shout", Emulator.getTexts().getValue("commands.keys.cmd_shout").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_shout.forgot_username"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);

        if (target == null) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_shout.user_not_found"), RoomChatMessageBubbles.ALERT);
            return true;
        } else {
            if (target.getHabboInfo().getCurrentRoom() == null) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_shout.hotel_view").replace("%user%", params[1]), RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        var message = new StringBuilder();
        if (params.length > 2) {
            for (int i = 2; i < params.length; i++) {
                message.append(params[i]).append(" ");
            }
        }

        target.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserShoutComposer(new RoomChatMessage(message.toString(), target, RoomChatMessageBubbles.NORMAL)).compose());
        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_shout").replace("%user%", params[1]).replace("%message%", message.toString()), RoomChatMessageBubbles.ALERT);
        return true;
    }
}
