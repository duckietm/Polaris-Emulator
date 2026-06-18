package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ModToolKickEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.modtools.kick").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
            return;
        }

        int userId = this.packet.readInt();
        String message = ModToolInputGuard.normalize(this.packet.readString());

        if (!ModToolTicketGuard.isPositiveId(userId) || !ModToolInputGuard.isSafeMessage(message)) {
            return;
        }

        Emulator.getGameEnvironment().getModToolManager().kick(this.client.getHabbo(), Emulator.getGameEnvironment().getHabboManager().getHabbo(userId), message);
    }
}
