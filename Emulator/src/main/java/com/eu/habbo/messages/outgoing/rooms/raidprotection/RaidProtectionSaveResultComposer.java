package com.eu.habbo.messages.outgoing.rooms.raidprotection;

import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionSettings;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official AIR 15 raid protection save result (3620).
 *
 * <p>The field order is not the obvious one: the client reads the room id, then the result code,
 * then resumes the settings snapshot from its <em>second</em> field. The room id is written once,
 * not twice.
 *
 * <p>The client closes the panel on result code 0 and leaves it open on anything else.
 *
 * <p>The nine snapshot appends are written out here rather than borrowed from
 * {@link RaidProtectionSettingsComposer}: the packet contract extractor reads composeInternal
 * statically and cannot follow a call into another class.
 */
public class RaidProtectionSaveResultComposer extends MessageComposer {
    private final int resultCode;
    private final RaidProtectionSettings settings;
    private final boolean incidentActive;
    private final int lastRaidAtSeconds;

    public RaidProtectionSaveResultComposer(
            int resultCode, RaidProtectionSettings settings, boolean incidentActive, int lastRaidAtSeconds) {
        this.resultCode = resultCode;
        this.settings = settings;
        this.incidentActive = incidentActive;
        this.lastRaidAtSeconds = lastRaidAtSeconds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RaidProtectionSaveResultComposer);
        this.response.appendInt(this.settings.getRoomId());
        this.response.appendInt(this.resultCode);
        this.response.appendBoolean(this.settings.isEnabled());
        this.response.appendInt(this.settings.getDetectionSensitivity());
        this.response.appendInt(this.settings.getActionType());
        this.response.appendInt(this.settings.getBanDurationSeconds());
        this.response.appendBoolean(this.settings.isGuardEnabled());
        this.response.appendInt(this.settings.getGuardDurationSeconds());
        this.response.appendInt(this.settings.getGuardSensitivity());
        this.response.appendBoolean(this.incidentActive);
        this.response.appendInt(this.lastRaidAtSeconds);
        return this.response;
    }
}
