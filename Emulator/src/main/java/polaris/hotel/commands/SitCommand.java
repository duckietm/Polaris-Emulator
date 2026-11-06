package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;

public class SitCommand extends Command {
    public SitCommand() {
        super(null, Emulator.getTexts().getValue("commands.keys.cmd_sit").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getRiding() == null)
            gameClient.getUser().getUserInfo().getCurrentRoom().makeSit(gameClient.getUser());
        return true;
    }
}
