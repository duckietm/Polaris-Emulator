package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.Map;

public class MassCreditsCommand extends Command {
    public MassCreditsCommand() {
        super(
                "cmd_masscredits",
                Emulator.getTexts().getValue("commands.keys.cmd_masscredits").split(";"));
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
                                Emulator.getTexts().getValue("commands.error.cmd_masscredits.invalid_amount"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (amount != 0) {
                int staffId = gameClient.getHabbo().getHabboInfo().getId();
                for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment()
                        .getHabboManager()
                        .getOnlineHabbos()
                        .entrySet()) {
                    Habbo habbo = set.getValue();

                    if (!CommandEconomy.credits(habbo, amount, "cmd_masscredits", staffId)) continue;

                    if (habbo.getHabboInfo().getCurrentRoom() != null)
                        habbo.whisper(
                                Emulator.getTexts()
                                        .getValue("commands.generic.cmd_credits.received")
                                        .replace("%amount%", amount + ""),
                                RoomChatMessageBubbles.ALERT);
                }
            }
            return true;
        }
        gameClient
                .getHabbo()
                .whisper(
                        Emulator.getTexts().getValue("commands.error.cmd_masscredits.invalid_amount"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
