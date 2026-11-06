package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class SoftKickCommand extends Command {
    public SoftKickCommand() {
        super(
                "cmd_softkick",
                Emulator.getTexts().getValue("commands.keys.cmd_softkick").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            final String username = params[1];
            final User user =
                    gameClient.getUser().getUserInfo().getCurrentRoom().getUser(username);

            if (user == null) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.keys.cmd_softkick_error")
                                        .replace("%user%", username),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (user == gameClient.getUser()) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.keys.cmd_softkick_error_self"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            final Room room = gameClient.getUser().getUserInfo().getCurrentRoom();

            if (room != null) {
                if (!(user.hasPermission(Permission.ACC_UNKICKABLE)
                        || user.hasPermission(Permission.ACC_SUPPORTTOOL)
                        || room.isOwner(user))) {
                    room.kickUser(user, false);
                }
            }
        }
        return true;
    }
}
