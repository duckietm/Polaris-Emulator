package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModToolUserRoomVisitsComposer;

public class ModToolRequestRoomVisitsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int userId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(userId)) {
                return;
            }

            HabboInfo habboInfo = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(userId);

            if (habboInfo != null) {
                this.client.sendResponse(new ModToolUserRoomVisitsComposer(habboInfo, Emulator.getGameEnvironment().getModToolManager().getUserRoomVisits(userId)));
            }
        }
    }
}
