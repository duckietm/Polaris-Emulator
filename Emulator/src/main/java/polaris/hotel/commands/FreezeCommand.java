package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class FreezeCommand extends Command {
    public FreezeCommand() {
        super(
                "cmd_freeze",
                Emulator.getTexts().getValue("commands.keys.cmd_freeze").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            User user = gameClient.getUser().getUserInfo().getCurrentRoom().getUser(params[1]);

            if (user == null) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_freeze.not_found")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else {
                if (CommandTargetGuard.refuse(gameClient.getUser(), user)) {
                    return true;
                }

                if (user.getRoomUnit().canWalk()) {
                    user.getRoomUnit().setCanWalk(false);
                    user.whisper(
                            Emulator.getTexts().getValue("commands.succes.cmd_freeze.frozen"),
                            RoomChatMessageBubbles.ALERT);
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.succes.cmd_freeze.user_frozen")
                                            .replace("%user%", params[1]),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                } else {
                    user.getRoomUnit().setCanWalk(true);
                    user.whisper(
                            Emulator.getTexts().getValue("commands.succes.cmd_freeze.unfrozen"),
                            RoomChatMessageBubbles.ALERT);
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.succes.cmd_freeze.user_unfrozen")
                                            .replace("%user%", params[1]),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }
            }
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_freeze.not_found")
                                    .replace("%user%", ""),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
