package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.gameclients.GameClient;

public class BotsCommand extends Command {
    public BotsCommand() {
        super("cmd_bots", Emulator.getTexts().getValue("commands.keys.cmd_bots").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getCurrentRoom() == null
                || !gameClient.getUser().getUserInfo().getCurrentRoom().hasRights(gameClient.getUser())) return false;

        StringBuilder data = new StringBuilder(Emulator.getTexts().getValue("total") + ": "
                + gameClient
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .getCurrentBots()
                        .values()
                        .size());

        for (Object bot : gameClient
                .getUser()
                .getUserInfo()
                .getCurrentRoom()
                .getCurrentBots()
                .values()) {
            if (bot instanceof Bot) {
                data.append("\r");
                data.append("<b>")
                        .append(Emulator.getTexts().getValue("generic.bot.name"))
                        .append("</b>: ")
                        .append(((Bot) bot).getName())
                        .append(" <b>")
                        .append(Emulator.getTexts().getValue("generic.bot.id"))
                        .append("</b>: ")
                        .append(((Bot) bot).getId());
            }
        }

        gameClient.getUser().alert(data.toString());

        return true;
    }
}
