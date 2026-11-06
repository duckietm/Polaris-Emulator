package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;

public class StandCommand extends Command {
    public StandCommand() {
        super(null, Emulator.getTexts().getValue("commands.keys.cmd_stand").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getRiding() == null)
            gameClient.getUser().getUserInfo().getCurrentRoom().makeStand(gameClient.getUser());
        return true;
    }
}
