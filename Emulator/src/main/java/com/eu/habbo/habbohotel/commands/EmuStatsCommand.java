package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;

public class EmuStatsCommand extends Command {
    public EmuStatsCommand() {
        super(Permission.ACC_MODTOOL_ROOM_INFO, new String[]{"emustats"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        gameClient.getHabbo().whisper("Emulator stats are available in the Nitro stats window.");
        return true;
    }
}
