package com.eu.habbo.messages.incoming.ambassadors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.plugin.events.support.SupportUserAlertedEvent;
import com.eu.habbo.plugin.events.support.SupportUserAlertedReason;

public class AmbassadorAlertCommandEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_AMBASSADOR)) {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.modtools.alert").replace("%username%", client.getHabbo().getHabboInfo().getUsername()).replace("%message%", "${notification.ambassador.alert.warning.message}"));
            return;
        }

        int userId = this.packet.readInt();

        Habbo habbo = this.client.getHabbo().getHabboInfo().getCurrentRoom().getHabbo(userId);

        if (habbo == null)
            return;

        var alertedEvent = new SupportUserAlertedEvent(client.getHabbo(), habbo, "${notification.ambassador.alert.warning.message}", SupportUserAlertedReason.AMBASSADOR);

        if (Emulator.getPluginManager().fireEvent(alertedEvent).isCancelled())
            return;

        habbo.getClient().sendResponse(new BubbleAlertComposer("ambassador.alert.warning"));
    }
}
