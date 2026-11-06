package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.DanceType;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.users.RoomUserDanceComposer;

public class RoomDanceCommand extends Command {
    public RoomDanceCommand() {
        super(
                "cmd_danceall",
                Emulator.getTexts().getValue("commands.keys.cmd_danceall").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            int danceId;

            try {
                danceId = Integer.parseInt(params[1]);
            } catch (Exception e) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_danceall.invalid_dance"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (danceId < 0 || danceId > 4) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_danceall.outside_bounds"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            for (User user : gameClient.getUser().getUserInfo().getCurrentRoom().getUsers()) {
                user.getRoomUnit().setDanceType(DanceType.values()[danceId]);
                user.getUserInfo()
                        .getCurrentRoom()
                        .sendComposer(new RoomUserDanceComposer(user.getRoomUnit()).compose());
            }
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_danceall.no_dance"),
                            RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
