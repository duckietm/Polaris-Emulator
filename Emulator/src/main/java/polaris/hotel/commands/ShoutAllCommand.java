package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class ShoutAllCommand extends Command {
    public ShoutAllCommand() {
        super(
                "cmd_shout_all",
                Emulator.getTexts().getValue("commands.keys.cmd_shout_all").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_shout_all.forgot_message"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        StringBuilder message = new StringBuilder();
        for (int i = 1; i < params.length; i++) {
            message.append(params[i]).append(" ");
        }

        for (User user : gameClient.getUser().getUserInfo().getCurrentRoom().getUsers()) {
            user.shout(message.toString());
        }

        return true;
    }
}
