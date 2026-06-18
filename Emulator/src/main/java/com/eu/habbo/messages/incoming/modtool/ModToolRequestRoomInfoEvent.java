package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModToolRoomInfoComposer;

public class ModToolRequestRoomInfoEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int roomId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(roomId)) {
                return;
            }

            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

            if (room != null) {
                this.client.sendResponse(new ModToolRoomInfoComposer(room));
            }
        } else {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.modtools.roominfo").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
        }
    }
}
