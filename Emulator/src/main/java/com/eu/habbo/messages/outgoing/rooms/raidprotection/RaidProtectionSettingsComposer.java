package com.eu.habbo.messages.outgoing.rooms.raidprotection;

import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionSnapshot;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 3877 (package_178/class_3242, parser package_230/class_3943): a room's raid protection
 * settings, as RaidProtectionSettingsSnapshot.readFromMessage reads them.
 */
public class RaidProtectionSettingsComposer extends MessageComposer {
    private final RaidProtectionSnapshot snapshot;

    public RaidProtectionSettingsComposer(RaidProtectionSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RaidProtectionSettingsComposer);
        this.response.appendInt(this.snapshot.settings().roomId());
        this.response.appendBoolean(this.snapshot.settings().enabled());
        this.response.appendInt(this.snapshot.settings().sensitivity());
        this.response.appendInt(this.snapshot.settings().actionType());
        this.response.appendInt(this.snapshot.settings().banDurationSeconds());
        this.response.appendBoolean(this.snapshot.settings().guardEnabled());
        this.response.appendInt(this.snapshot.settings().guardDurationSeconds());
        this.response.appendInt(this.snapshot.settings().guardSensitivity());
        this.response.appendBoolean(this.snapshot.incidentActive());
        this.response.appendInt(this.snapshot.lastRaidAt());
        return this.response;
    }

    public RaidProtectionSnapshot getSnapshot() {
        return this.snapshot;
    }
}
