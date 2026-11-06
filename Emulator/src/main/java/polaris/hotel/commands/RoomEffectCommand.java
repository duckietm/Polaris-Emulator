package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class RoomEffectCommand extends Command {
    public RoomEffectCommand() {
        super(
                "cmd_roomeffect",
                Emulator.getTexts().getValue("commands.keys.cmd_roomeffect").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_roomeffect.no_effect"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        try {
            int effectId = Integer.parseInt(params[1]);

            if (effectId >= 0) {
                Room room = gameClient.getUser().getUserInfo().getCurrentRoom();
                for (User user : room.getUsers()) {
                    room.giveEffect(user, effectId, -1);
                }

                return true;
            } else {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_roomeffect.positive"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        } catch (Exception e) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_roomeffect.numbers_only"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
