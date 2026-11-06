package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class SayAllCommand extends Command {
    public SayAllCommand() {
        super(
                "cmd_say_all",
                Emulator.getTexts().getValue("commands.keys.cmd_say_all").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_say_all.forgot_message"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        StringBuilder message = new StringBuilder();
        for (int i = 1; i < params.length; i++) {
            message.append(params[i]).append(" ");
        }

        for (User user : gameClient.getUser().getUserInfo().getCurrentRoom().getUsers()) {
            user.talk(message.toString());
        }

        return true;
    }
}
