package com.eu.habbo.messages.outgoing.rooms.raidprotection;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official AIR 15 raid protection capability (734): whether this user may manage the room's raid
 * protection.
 *
 * <p>The client keys the capability on the room and throws it away as soon as the user leaves, so
 * this is sent on entering a room rather than once per session.
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
