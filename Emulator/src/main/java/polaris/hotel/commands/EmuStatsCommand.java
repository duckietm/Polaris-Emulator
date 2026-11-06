package polaris.hotel.commands;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;

public class EmuStatsCommand extends Command {
    public EmuStatsCommand() {
        super(Permission.ACC_MODTOOL_ROOM_INFO, new String[] {"emustats"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        gameClient.getUser().whisper("Emulator stats are available in the Nitro stats window.");
        return true;
    }
}
