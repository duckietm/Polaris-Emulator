package com.eu.habbo.messages.outgoing.rooms.raidprotection;

import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionSnapshot;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 958 (package_178/class_2896, parser package_230/class_4240): the answer to a save. The room id,
 * the result (0 saved, 1 feature off, 2 room gone, 3 no rights, 4 invalid, 5 confirmation needed,
 * 6 not saved), then the settings now in force, read with RaidProtectionSettingsSnapshot.readAfterRoomId.
 */
public class RaidProtectionSaveResultComposer extends MessageComposer {
    private final int resultCode;
    private final RaidProtectionSnapshot snapshot;

    public RaidProtectionSaveResultComposer(int resultCode, RaidProtectionSnapshot snapshot) {
        this.resultCode = resultCode;
        this.snapshot = snapshot;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RaidProtectionSaveResultComposer);
        this.response.appendInt(this.snapshot.settings().roomId());
        this.response.appendInt(this.resultCode);
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

    public int getResultCode() {
        return this.resultCode;
    }

    public RaidProtectionSnapshot getSnapshot() {
        return this.snapshot;
    }
}
