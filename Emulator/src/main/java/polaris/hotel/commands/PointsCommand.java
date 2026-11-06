package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class PointsCommand extends Command {
    public PointsCommand() {
        super("cmd_points", text("commands.keys.cmd_points").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length >= 3) {
            User user = Emulator.getGameServer().getGameClientManager().getUser(params[1]);

            if (user != null) {
                try {
                    int type = Emulator.getConfig().getInt("seasonal.primary.type");

                    if (params.length == 4) {
                        try {
                            type = Integer.parseInt(params[3]);
                        } catch (Exception e) {
                            gameClient
                                    .getUser()
                                    .whisper(
                                            text("commands.error.cmd_points.invalid_type")
                                                    .replace(
                                                            "%types%",
                                                            Emulator.getConfig()
                                                                    .getValue("seasonal.types")
                                                                    .replace(";", ", ")),
                                            RoomChatMessageBubbles.ALERT);
                            return true;
                        }
                    }

                    int amount;

                    try {
                        amount = Integer.parseInt(params[2]);
                    } catch (Exception e) {
                        gameClient
                                .getUser()
                                .whisper(
                                        text("commands.error.cmd_points.invalid_amount"), RoomChatMessageBubbles.ALERT);
                        return true;
                    }

                    if (amount != 0) {
                        // Recorded as done by the staff member, and reported only when the ledger took it.
                        if (!CommandEconomy.points(
                                user,
                                type,
                                amount,
                                "cmd_points",
                                gameClient.getUser().getUserInfo().getId())) {
                            gameClient
                                    .getUser()
                                    .whisper(
                                            text(
                                                            "commands.error.cmd_points.failed",
                                                            "The points of %user% could not be changed.")
                                                    .replace("%user%", params[1]),
                                            RoomChatMessageBubbles.ALERT);
                            return true;
                        }

                        if (user.getUserInfo().getCurrentRoom() != null)
                            user.whisper(
                                    text("commands.generic.cmd_points.received")
                                            .replace("%amount%", amount + "")
                                            .replace("%type%", text("seasonal.name." + type)),
                                    RoomChatMessageBubbles.ALERT);
                        else
                            user.alert(text("commands.generic.cmd_points.received")
                                    .replace("%amount%", amount + "")
                                    .replace("%type%", text("seasonal.name." + type)));

                        // user.getClient().sendResponse(new
                        // UserPointsComposer(user.getUserInfo().getCurrencyAmount(type), amount, type));

                        gameClient
                                .getUser()
                                .whisper(
                                        text("commands.succes.cmd_points.send")
                                                .replace("%amount%", amount + "")
                                                .replace("%user%", params[1])
                                                .replace("%type%", text("seasonal.name." + type)),
                                        RoomChatMessageBubbles.ALERT);

                    } else {
                        gameClient
                                .getUser()
                                .whisper(
                                        text("commands.error.cmd_points.invalid_amount"), RoomChatMessageBubbles.ALERT);
                    }
                } catch (NumberFormatException e) {
                    gameClient
                            .getUser()
                            .whisper(text("commands.error.cmd_points.invalid_amount"), RoomChatMessageBubbles.ALERT);
                }
            } else {
                gameClient
                        .getUser()
                        .whisper(
                                text("commands.error.cmd_points.user_offline").replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
            }
        } else {
            gameClient
                    .getUser()
                    .whisper(text("commands.error.cmd_points.invalid_amount"), RoomChatMessageBubbles.ALERT);
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
