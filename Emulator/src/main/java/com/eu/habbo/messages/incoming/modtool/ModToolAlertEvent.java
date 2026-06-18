package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.support.SupportUserAlertedReason;

public class ModToolAlertEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int userId = this.packet.readInt();
            String message = ModToolInputGuard.normalize(this.packet.readString());

            if (!ModToolTicketGuard.isPositiveId(userId) || !ModToolInputGuard.isSafeMessage(message)) {
                return;
            }

            Habbo alertedUser = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

            if (alertedUser != null)
                Emulator.getGameEnvironment().getModToolManager().alert(this.client.getHabbo(), alertedUser, message, SupportUserAlertedReason.ALERT);
        } else {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.modtools.kick").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
        }
    }
}
