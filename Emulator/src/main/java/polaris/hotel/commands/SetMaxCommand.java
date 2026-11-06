package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class SetMaxCommand extends Command {
    public SetMaxCommand() {
        super(
                "cmd_setmax",
                Emulator.getTexts().getValue("commands.keys.cmd_setmax").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length >= 2) {
            int max;
            try {
                max = Integer.parseInt(params[1]);
            } catch (Exception e) {
                // Not a number: same answer as an out of range one instead of saying the line.
                max = -1;
            }

            if (gameClient.getUser().getUserInfo().getCurrentRoom() == null) {
                return true;
            }

            // A value-2 permission passes for anyone with rights here: the room's limit is its owner's call.
            if (!gameClient.getUser().getUserInfo().getCurrentRoom().isOwner(gameClient.getUser())) {
                gameClient.getUser().whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (max > 0 && max < 9999) {
                gameClient.getUser().getUserInfo().getCurrentRoom().setUsersMax(max);
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.success.cmd_setmax")
                                        .replace("%value%", max + ""),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_setmax.invalid_number"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_setmax.forgot_number"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
