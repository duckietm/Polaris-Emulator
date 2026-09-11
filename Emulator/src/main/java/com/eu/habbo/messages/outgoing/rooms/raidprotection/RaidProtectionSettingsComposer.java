package com.eu.habbo.messages.outgoing.rooms.raidprotection;

import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionSettings;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official AIR 15 raid protection settings (3553): the room's stored configuration plus the two
 * pieces of state the panel shows next to it.
 *
 * <p>The field order is the client's {@code RaidProtectionSettingsSnapshot.readFromMessage}. The
 * same ten fields, minus the leading room id, are appended by {@link
 * RaidProtectionSaveResultComposer}.
 */
public class RaidProtectionSettingsComposer extends MessageComposer {
    private final RaidProtectionSettings settings;
    private final boolean incidentActive;
    private final int lastRaidAtSeconds;

    public RaidProtectionSettingsComposer(
            RaidProtectionSettings settings, boolean incidentActive, int lastRaidAtSeconds) {
        this.settings = settings;
        this.incidentActive = incidentActive;
        this.lastRaidAtSeconds = lastRaidAtSeconds;
    }

    /** Appends the snapshot without the room id, which both composers write themselves. */
    static void appendAfterRoomId(
            ServerMessage response, RaidProtectionSettings settings, boolean incidentActive, int lastRaidAtSeconds) {
        response.appendBoolean(settings.isEnabled());
        response.appendInt(settings.getDetectionSensitivity());
        response.appendInt(settings.getActionType());
        response.appendInt(settings.getBanDurationSeconds());
        response.appendBoolean(settings.isGuardEnabled());
        response.appendInt(settings.getGuardDurationSeconds());
        response.appendInt(settings.getGuardSensitivity());
        response.appendBoolean(incidentActive);
        response.appendInt(lastRaidAtSeconds);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RaidProtectionSettingsComposer);
        this.response.appendInt(this.settings.getRoomId());
        appendAfterRoomId(this.response, this.settings, this.incidentActive, this.lastRaidAtSeconds);
        return this.response;
    }
}
