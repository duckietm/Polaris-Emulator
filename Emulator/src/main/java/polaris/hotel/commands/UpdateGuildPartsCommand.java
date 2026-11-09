package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;

public class UpdateGuildPartsCommand extends Command {
    public UpdateGuildPartsCommand() {
        super(
                "cmd_update_guildparts",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_update_guildparts")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        reload();
        return true;
    }

    /** The reload itself, without the whisper: also run by the RCON reloadcontent command. */
    public static void reload() {
        Emulator.getGameEnvironment().getGuildManager().loadGuildParts();
        Emulator.getBadgeImager().reload();
    }
}
