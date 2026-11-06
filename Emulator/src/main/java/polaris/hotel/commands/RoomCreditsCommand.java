package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class RoomCreditsCommand extends Command {
    public RoomCreditsCommand() {
        super(
                "cmd_roomcredits",
                Emulator.getTexts().getValue("commands.keys.cmd_roomcredits").split(";"));
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
                                Emulator.getTexts().getValue("commands.error.cmd_masscredits.invalid_amount"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (amount != 0) {
                int staffId = gameClient.getUser().getUserInfo().getId();
                final String message = Emulator.getTexts()
                        .getValue("commands.generic.cmd_credits.received")
                        .replace("%amount%", amount + "");
                for (User user :
                        gameClient.getUser().getUserInfo().getCurrentRoom().getUsers()) {
                    if (!CommandEconomy.credits(user, amount, "cmd_roomcredits", staffId)) continue;
                    user.whisper(message, RoomChatMessageBubbles.ALERT);
                }
            }
            return true;
        }
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.error.cmd_masscredits.invalid_amount"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
