package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.users.RoomUserTalkComposer;

public class SayCommand extends Command {
    public SayCommand() {
        super("cmd_say", Emulator.getTexts().getValue("commands.keys.cmd_say").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_say.forgot_username"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        User target = Emulator.getGameEnvironment().getUserManager().getUser(params[1]);

        if (target == null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_say.user_not_found"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        } else {
            if (target.getUserInfo().getCurrentRoom() == null) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_say.hotel_view")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        if (CommandTargetGuard.refuse(gameClient.getUser(), target)) {
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
        chat.filterWrittenBy(gameClient.getUser());
        target.getUserInfo().getCurrentRoom().sendComposer(new RoomUserTalkComposer(chat).compose());
        chat.logWrittenBy(gameClient.getUser(), "say");
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts()
                                .getValue("commands.succes.cmd_say")
                                .replace("%user%", params[1])
                                .replace("%message%", message.toString()),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
