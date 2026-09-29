package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;

public class UnloadRoomCommand extends Command {

    public UnloadRoomCommand() {
        super(
                "cmd_unload",
                Emulator.getTexts().getValue("commands.keys.cmd_unload").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom().getOwnerId()
                        == gameClient.getHabbo().getHabboInfo().getId()
                || gameClient.getHabbo().hasPermission(Permission.ACC_UNLOAD_ANY_ROOM)) {
            Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

            room.dispose();
            return true;
        }
        return false;
    }
}
