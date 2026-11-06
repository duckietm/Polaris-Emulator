package polaris.hotel.commands;

import java.util.Map;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class MassPixelsCommand extends Command {
    public MassPixelsCommand() {
        super(
                "cmd_massduckets",
                Emulator.getTexts().getValue("commands.keys.cmd_massduckets").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            int amount;

            try {
                amount = Integer.parseInt(params[1]);
            } catch (Exception e) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_massduckets.invalid_amount"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (amount != 0) {
                int staffId = gameClient.getUser().getUserInfo().getId();
                for (Map.Entry<Integer, User> set : Emulator.getGameEnvironment()
                        .getUserManager()
                        .getOnlineUsers()
                        .entrySet()) {
                    User user = set.getValue();

                    if (!CommandEconomy.points(user, 0, amount, "cmd_massduckets", staffId)) continue;

                    if (user.getUserInfo().getCurrentRoom() != null)
                        user.whisper(
                                Emulator.getTexts()
                                        .getValue("commands.generic.cmd_duckets.received")
                                        .replace("%amount%", amount + ""),
                                RoomChatMessageBubbles.ALERT);
                }
            }
            return true;
        }
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.error.cmd_massduckets.invalid_amount"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
