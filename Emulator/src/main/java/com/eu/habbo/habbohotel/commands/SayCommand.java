package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserTalkComposer;

public class SayCommand extends Command {
    public SayCommand() {
        super("cmd_say", Emulator.getTexts().getValue("commands.keys.cmd_say").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_say.forgot_username"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);

        if (target == null) {
            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_say.user_not_found"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        } else {
            if (target.getHabboInfo().getCurrentRoom() == null) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_say.hotel_view")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        if (CommandTargetGuard.refuse(gameClient.getHabbo(), target)) {
            return true;
        }

        StringBuilder message = new StringBuilder();
        if (params.length > 2) {
            for (int i = 2; i < params.length; i++) {
                message.append(params[i]).append(" ");
            }
        }

        // The line goes through the room's word filter, judged on the moderator who wrote it, and into
        // the room chat log with their name: it used to reach the room unfiltered and unrecorded.
        RoomChatMessage chat = new RoomChatMessage(message.toString(), target, RoomChatMessageBubbles.NORMAL);
        chat.filterWrittenBy(gameClient.getHabbo());
        target.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserTalkComposer(chat).compose());
        chat.logWrittenBy(gameClient.getHabbo(), "say");
        gameClient
                .getHabbo()
                .whisper(
                        Emulator.getTexts()
                                .getValue("commands.succes.cmd_say")
                                .replace("%user%", params[1])
                                .replace("%message%", message.toString()),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
