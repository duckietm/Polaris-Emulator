package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

public class SummonCommand extends Command {
    public SummonCommand() {
        super(
                "cmd_summon",
                Emulator.getTexts().getValue("commands.keys.cmd_summon").split(";"));
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
                                        .getValue("commands.error.cmd_summon.not_found")
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
                                        .getValue("commands.generic.cmd_summon.self")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (CommandTargetGuard.refuse(gameClient.getUser(), user)) {
                return true;
            }

            if (gameClient.getUser().getUserInfo().getCurrentRoom()
                    == user.getUserInfo().getCurrentRoom()) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.generic.cmd_summon.same_room")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            Room room = user.getUserInfo().getCurrentRoom();
            if (room != null) {
                Emulator.getGameEnvironment().getRoomManager().logExit(user);

                room.removeUser(user, true);

                user.getUserInfo().setCurrentRoom(null);
            }

            Emulator.getGameEnvironment()
                    .getRoomManager()
                    .enterRoom(
                            user,
                            gameClient.getUser().getUserInfo().getCurrentRoom().getId(),
                            "",
                            true);

            user.getClient()
                    .sendResponse(new ForwardToRoomComposer(
                            gameClient.getUser().getUserInfo().getCurrentRoom().getId()));

            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_summon.summoned")
                                    .replace("%user%", params[1]),
                            RoomChatMessageBubbles.ALERT);

            user.alert(Emulator.getTexts()
                    .getValue("commands.generic.cmd_summon.been_summoned")
                    .replace("%user%", gameClient.getUser().getUserInfo().getUsername()));

            return true;
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_summon.forgot_username"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
