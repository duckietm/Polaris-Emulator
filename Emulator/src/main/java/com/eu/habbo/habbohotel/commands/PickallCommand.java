package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;

public class PickallCommand extends Command {
    public PickallCommand() {
        super(
                "cmd_pickall",
                Emulator.getTexts().getValue("commands.keys.cmd_pickall").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

        if (room != null) {
            if (room.isOwner(gameClient.getHabbo())) {
                // Builders Club furni stay: as in Habbo, only :pickallbc takes them out.
                room.getItemManager().ejectAllExceptBuildersClub();
                return true;
            }

            room.ejectUserFurni(gameClient.getHabbo().getHabboInfo().getId());
        }

        return true;
    }
}
