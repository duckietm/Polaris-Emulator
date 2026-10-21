package com.eu.habbo.messages.outgoing.rooms.raidprotection;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 2671 (package_178/class_2997, parser package_230/class_4062): whether the viewer may manage the
 * raid protection of the room they entered. AIR shows the room info "raid protection" button on it.
 */
public class RaidProtectionCapabilityComposer extends MessageComposer {
    private final int roomId;
    private final boolean canManage;

    public RaidProtectionCapabilityComposer(int roomId, boolean canManage) {
        this.roomId = roomId;
        this.canManage = canManage;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RaidProtectionCapabilityComposer);
        this.response.appendInt(this.roomId);
        this.response.appendBoolean(this.canManage);
        return this.response;
    }
}
