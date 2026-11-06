package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;

public class UpdateBotsCommand extends Command {
    public UpdateBotsCommand() {
        super(
                "cmd_update_bots",
                Emulator.getTexts().getValue("commands.keys.cmd_update_bots").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        return Emulator.getGameEnvironment().getBotManager().reload();
    }
}
