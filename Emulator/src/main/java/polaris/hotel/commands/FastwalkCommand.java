package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class FastwalkCommand extends Command {
    public FastwalkCommand() {
        super(
                "cmd_fastwalk",
                Emulator.getTexts().getValue("commands.keys.cmd_fastwalk").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
            if (gameClient.getUser().getUserInfo().getRiding() != null) return true;

            User user = gameClient.getUser();

            if (params.length >= 2) {
                String username = params[1];

                user = gameClient.getUser().getUserInfo().getCurrentRoom().getUser(username);

                if (user == null) {
                    gameClient
                            .getUser()
                            .whisperLocalized(
                                    "generic.user.not_found", "%user%", username, RoomChatMessageBubbles.ALERT);
                    return true;
                }
            }
            user.getRoomUnit().setFastWalk(!user.getRoomUnit().isFastWalk());
        }

        return true;
    }
}
