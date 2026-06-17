package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

import java.util.Map;

/**
 * Mirrors :ha — staff alert with sender attribution, broadcast to
 * every online user whose `blockStaffAlerts` flag isn't set. Composed
 * once and forwarded by reference (sendResponse compiles to the same
 * underlying buffer) so the broadcast is O(N habbos) wire writes,
 * not O(N) compose calls.
 */
public class HousekeepingSendHotelAlertEvent extends MessageHandler {
    private static final String ACTION_KEY = "hotel.alert";

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return;
        }

        String message = HousekeepingInputGuard.normalize(this.packet.readString());

        if (message.isEmpty()) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.alert_empty"));
            return;
        }

        if (!HousekeepingInputGuard.isWithinLimit(message, HousekeepingInputGuard.MAX_ALERT_LENGTH)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.input_too_long"));
            return;
        }

        String body = message + "\r\n-" + this.client.getHabbo().getHabboInfo().getUsername();
        ServerMessage broadcast = new StaffAlertWithLinkComposer(body, "").compose();

        int reached = 0;

        for (Map.Entry<Integer, Habbo> entry : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().entrySet()) {
            Habbo habbo = entry.getValue();

            if (habbo == null || habbo.getClient() == null) continue;
            if (habbo.getHabboStats() != null && habbo.getHabboStats().blockStaffAlerts) continue;

            habbo.getClient().sendResponse(broadcast);
            reached++;
        }

        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, 0, "reached=" + reached + " message=" + HousekeepingInputGuard.auditValue(message),
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, reached, ""));
    }
}
