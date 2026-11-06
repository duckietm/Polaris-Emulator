package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class PixelCommand extends Command {
    public PixelCommand() {
        super("cmd_duckets", text("commands.keys.cmd_duckets").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 3) {
            User user = Emulator.getGameServer().getGameClientManager().getUser(params[1]);

            if (user != null) {
                try {
                    if (Integer.parseInt(params[2]) != 0) {
                        // Recorded as done by the staff member, and reported only when the ledger took it.
                        if (!CommandEconomy.points(
                                user,
                                0,
                                Integer.parseInt(params[2]),
                                "cmd_duckets",
                                gameClient.getUser().getUserInfo().getId())) {
                            gameClient
                                    .getUser()
                                    .whisper(
                                            text(
                                                            "commands.error.cmd_duckets.failed",
                                                            "The duckets of %user% could not be changed.")
                                                    .replace("%user%", params[1]),
                                            RoomChatMessageBubbles.ALERT);
                            return true;
                        }
                        if (user.getUserInfo().getCurrentRoom() != null)
                            user.whisper(
                                    text("commands.generic.cmd_duckets.received")
                                            .replace("%amount%", Integer.valueOf(params[2]) + ""),
                                    RoomChatMessageBubbles.ALERT);
                        else
                            user.alert(text("commands.generic.cmd_duckets.received")
                                    .replace("%amount%", Integer.valueOf(params[2]) + ""));

                        gameClient
                                .getUser()
                                .whisper(
                                        text("commands.succes.cmd_duckets.send")
                                                .replace("%amount%", Integer.valueOf(params[2]) + "")
                                                .replace("%user%", params[1]),
                                        RoomChatMessageBubbles.ALERT);

                    } else {
                        gameClient
                                .getUser()
                                .whisper(
                                        text("commands.error.cmd_duckets.invalid_amount"),
                                        RoomChatMessageBubbles.ALERT);
                    }
                } catch (NumberFormatException e) {
                    gameClient
                            .getUser()
                            .whisper(text("commands.error.cmd_duckets.invalid_amount"), RoomChatMessageBubbles.ALERT);
                }
            } else {
                gameClient
                        .getUser()
                        .whisper(
                                text("commands.error.cmd_duckets.user_offline").replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
            }
        } else {
            gameClient
                    .getUser()
                    .whisper(text("commands.error.cmd_duckets.invalid_amount"), RoomChatMessageBubbles.ALERT);
        }
        return true;
    }

    private static String text(String key) {
        return text(key, null);
    }

    private static String text(String key, String fallback) {
        return fallback == null
                ? Emulator.getTexts().getValue(key)
                : Emulator.getTexts().getValue(key, fallback);
    }
}
