package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModToolUserChatlogComposer;

public class ModToolRequestUserChatlogEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int userId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(userId)) {
                return;
            }

            HabboInfo habboInfo = HabboManager.getOfflineHabboInfo(userId);
            if (habboInfo == null) {
                return;
            }
            String username = habboInfo.getUsername();

            this.client.sendResponse(new ModToolUserChatlogComposer(Emulator.getGameEnvironment().getModToolManager().getUserRoomVisitsAndChatlogs(userId), userId, username));
        } else {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.chatlog").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
        }
    }
}
