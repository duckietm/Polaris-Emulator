package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModToolRoomChatlogComposer;

public class ModToolRequestRoomChatlogEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            this.packet.readInt();
            int roomId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(roomId)) {
                return;
            }

            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

            if (room != null)
                this.client.sendResponse(new ModToolRoomChatlogComposer(room, Emulator.getGameEnvironment().getModToolManager().getRoomChatlog(room.getId())));
        } else {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.modtools.chatlog").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
        }
    }
}
