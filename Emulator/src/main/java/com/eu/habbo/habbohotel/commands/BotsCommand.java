package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;

public class BotsCommand extends Command {
    public BotsCommand() {
        super("cmd_bots", Emulator.getTexts().getValue("commands.keys.cmd_bots").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() == null || !gameClient.getHabbo().getHabboInfo().getCurrentRoom().hasRights(gameClient.getHabbo()))
            return false;

        StringBuilder data = new StringBuilder(Emulator.getTexts().getValue("total") + ": " + gameClient.getHabbo().getHabboInfo().getCurrentRoom().getCurrentBots().values().size());

        for (Object bot : gameClient.getHabbo().getHabboInfo().getCurrentRoom().getCurrentBots().values()) {
            if (bot instanceof Bot) {
                data.append("\r");
                data.append("<b>").append(Emulator.getTexts().getValue("generic.bot.name")).append("</b>: ").append(((Bot) bot).getName()).append(" <b>").append(Emulator.getTexts().getValue("generic.bot.id")).append("</b>: ").append(((Bot) bot).getId());
            }
        }

        gameClient.getHabbo().alert(data.toString());

        return true;
    }
}
