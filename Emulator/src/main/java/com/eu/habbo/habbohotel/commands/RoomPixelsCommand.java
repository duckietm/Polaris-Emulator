package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class RoomPixelsCommand extends Command {
    public RoomPixelsCommand() {
        super(
                "cmd_roompixels",
                Emulator.getTexts().getValue("commands.keys.cmd_roompixels").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            int amount;

            try {
                amount = Integer.parseInt(params[1]);
            } catch (Exception e) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_massduckets.invalid_amount"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (amount != 0) {
                int staffId = gameClient.getHabbo().getHabboInfo().getId();
                final String message = Emulator.getTexts()
                        .getValue("commands.generic.cmd_duckets.received")
                        .replace("%amount%", amount + "");
                for (Habbo habbo :
                        gameClient.getHabbo().getHabboInfo().getCurrentRoom().getHabbos()) {
                    if (!CommandEconomy.points(habbo, 0, amount, "cmd_roompixels", staffId)) continue;
                    habbo.whisper(message, RoomChatMessageBubbles.ALERT);
                }
            }
            return true;
        }
        gameClient
                .getHabbo()
                .whisper(
                        Emulator.getTexts().getValue("commands.error.cmd_massduckets.invalid_amount"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
