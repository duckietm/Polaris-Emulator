package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;

public class RoomKickCommand extends Command {
    public RoomKickCommand() {
        super("cmd_kickall", Emulator.getTexts().getValue("commands.keys.cmd_kickall").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        final Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room != null) {
            if (params.length > 1) {
                var message = new StringBuilder();
                for (int i = 1; i < params.length; i++) {
                    message.append(params[i]).append(" ");
                }
                room.sendComposer(new GenericAlertComposer(message + "\r\n-" + gameClient.getHabbo().getHabboInfo().getUsername()).compose());
            }

            for (Habbo habbo : room.getHabbos()) {
                if (!(habbo.hasPermission(Permission.ACC_UNKICKABLE) || habbo.hasPermission(Permission.ACC_SUPPORTTOOL) || room.isOwner(habbo))) {
                    room.kickHabbo(habbo, true);
                }
            }
        }
        return true;
    }
}
