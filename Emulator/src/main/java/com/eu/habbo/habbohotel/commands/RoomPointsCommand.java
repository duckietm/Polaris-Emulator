package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class RoomPointsCommand extends Command {
    public RoomPointsCommand() {
        super(
                "cmd_roompoints",
                Emulator.getTexts().getValue("commands.keys.cmd_roompoints").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        int type = Emulator.getConfig().getInt("seasonal.primary.type");
        String amountString;
        if (params.length == 3) {
            try {
                amountString = params[1];

                type = Integer.parseInt(params[2]);
            } catch (Exception e) {
                gameClient
                        .getHabbo()
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
                    .getHabbo()
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
                    .getHabbo()
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
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_masspoints.invalid_amount"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (amount != 0) {
            int staffId = gameClient.getHabbo().getHabboInfo().getId();
            final String message = Emulator.getTexts()
                    .getValue("commands.generic.cmd_points.received")
                    .replace("%amount%", amount + "")
                    .replace("%type%", Emulator.getTexts().getValue("seasonal.name." + type));

            for (Habbo habbo :
                    gameClient.getHabbo().getHabboInfo().getCurrentRoom().getHabbos()) {
                if (!CommandEconomy.points(habbo, type, amount, "cmd_roompoints", staffId)) continue;
                habbo.whisper(message, RoomChatMessageBubbles.ALERT);
            }
        }
        return true;
    }
}
