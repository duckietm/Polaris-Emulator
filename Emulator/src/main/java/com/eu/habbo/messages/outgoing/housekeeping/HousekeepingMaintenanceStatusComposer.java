package com.eu.habbo.messages.outgoing.housekeeping;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Maintenance as the panel shows it: switched on or not, the rank that may still log in, its message and a running countdown. */
public class HousekeepingMaintenanceStatusComposer extends MessageComposer {
    private final boolean enabled;
    private final int minRank;
    private final String message;
    private final int countdownEndsAt;

    public HousekeepingMaintenanceStatusComposer(boolean enabled, int minRank, String message, int countdownEndsAt) {
        this.enabled = enabled;
        this.minRank = minRank;
        this.message = message == null ? "" : message;
        this.countdownEndsAt = countdownEndsAt;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HousekeepingMaintenanceStatusComposer);
        this.response.appendBoolean(this.enabled);
        this.response.appendInt(this.minRank);
        this.response.appendString(this.message);
        this.response.appendInt(this.countdownEndsAt);
        return this.response;
    }
}
