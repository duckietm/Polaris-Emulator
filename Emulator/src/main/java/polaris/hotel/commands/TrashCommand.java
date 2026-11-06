package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;

/** It only whispered that the feature was removed, so it is no longer registered; the class stays for plugins. */
@Deprecated
public class TrashCommand extends Command {
    public TrashCommand() {
        super(
                "cmd_trash",
                Emulator.getTexts().getValue("commands.keys.cmd_trash").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        gameClient.getUser().whisper("Sorry. Lulz mode removed |");
        return false;
    }
}
