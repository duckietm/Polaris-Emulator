package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class UnbanCommand extends Command {
    public UnbanCommand() {
        super(
                "cmd_unban",
                Emulator.getTexts().getValue("commands.keys.cmd_unban").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 1) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_unban.not_specified"),
                            RoomChatMessageBubbles.ALERT);
        } else {
            if (Emulator.getGameEnvironment().getModToolManager().unban(params[1], gameClient.getUser())) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_unban.success")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
            } else {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_unban.not_found")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
            }
        }

        return true;
    }
}
