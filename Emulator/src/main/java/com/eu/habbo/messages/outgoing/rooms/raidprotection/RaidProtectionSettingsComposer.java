package com.eu.habbo.messages.outgoing.rooms.raidprotection;

import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionSettings;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official AIR 15 raid protection settings (3553): the room's stored configuration plus the two
 * pieces of state the panel shows next to it.
 *
 * <p>The field order is the client's {@code RaidProtectionSettingsSnapshot.readFromMessage}.
 * {@link RaidProtectionSaveResultComposer} repeats these appends rather than calling in here: the
 * packet contract extractor reads composeInternal statically and refuses a delegated serializer.
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

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RaidProtectionSettingsComposer);
        this.response.appendInt(this.settings.getRoomId());
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
