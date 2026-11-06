package polaris.hotel.commands;

import java.util.Map;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class MassPointsCommand extends Command {
    public MassPointsCommand() {
        super(
                "cmd_masspoints",
                Emulator.getTexts().getValue("commands.keys.cmd_masspoints").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        int type = Emulator.getConfig().getInt("seasonal.primary.type");
        String amountString;
        if (params.length == 3) {
            amountString = params[1];
            try {
                type = Integer.parseInt(params[2]);
            } catch (Exception e) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_masspoints.invalid_type")
                                        .replace(
                                                "%types%",
                                                Emulator.getConfig()
                                                        .getValue("seasonal.types")
                                                        .replace(";", ", ")),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

        } else if (params.length == 2) {
            amountString = params[1];
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_masspoints.invalid_amount"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        boolean found = false;
        for (String s : Emulator.getConfig().getValue("seasonal.types").split(";")) {
            if (s.equalsIgnoreCase(type + "")) {
                found = true;
                break;
            }
        }

        if (!found) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_masspoints.invalid_type")
                                    .replace(
                                            "%types%",
                                            Emulator.getConfig()
                                                    .getValue("seasonal.types")
                                                    .replace(";", ", ")),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        int amount;

        try {
            amount = Integer.parseInt(amountString);
        } catch (Exception e) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_masspoints.invalid_amount"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (amount != 0) {
            int staffId = gameClient.getUser().getUserInfo().getId();
            String message = Emulator.getTexts()
                    .getValue("commands.generic.cmd_points.received")
                    .replace("%amount%", amount + "")
                    .replace("%type%", Emulator.getTexts().getValue("seasonal.name." + type));

            for (Map.Entry<Integer, User> set : Emulator.getGameEnvironment()
                    .getUserManager()
                    .getOnlineUsers()
                    .entrySet()) {
                User user = set.getValue();
                if (!CommandEconomy.points(user, type, amount, "cmd_masspoints", staffId)) continue;

                if (user.getUserInfo().getCurrentRoom() != null) user.whisper(message, RoomChatMessageBubbles.ALERT);
                else user.alert(message);
            }
        }
        return true;
    }
}
