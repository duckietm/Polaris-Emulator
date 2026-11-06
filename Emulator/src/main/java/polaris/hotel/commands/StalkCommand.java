package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.RoomDataComposer;

public class StalkCommand extends Command {
    public StalkCommand() {
        super(
                "cmd_stalk",
                Emulator.getTexts().getValue("commands.keys.cmd_stalk").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getCurrentRoom() == null) return true;

        if (params.length >= 2) {
            User user = Emulator.getGameEnvironment().getUserManager().getUser(params[1]);

            if (user == null) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_stalk.not_found")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (user.getUserInfo().getCurrentRoom() == null) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_stalk.not_room")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (gameClient
                    .getUser()
                    .getUserInfo()
                    .getUsername()
                    .equals(user.getUserInfo().getUsername())) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.generic.cmd_stalk.self")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (gameClient.getUser().getUserInfo().getCurrentRoom()
                    == user.getUserInfo().getCurrentRoom()) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.generic.cmd_stalk.same_room")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            gameClient.sendResponse(
                    new RoomDataComposer(user.getUserInfo().getCurrentRoom(), gameClient.getUser(), true, false));
            // gameClient.sendResponse(new ForwardToRoomComposer(user.getUserInfo().getCurrentRoom().getId()));
            return true;
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_stalk.forgot_username"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
