package com.eu.habbo.messages.incoming.rooms.raidprotection;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.raidprotection.RaidProtectionSettingsComposer;

/**
 * Official AIR 15 raid protection settings request (206): the user opened the panel for a room.
 *
 * <p>The requested room must be the one the user is standing in, which is what the client itself
 * enforces before sending.
 */
public class RaidProtectionSettingsRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();
        Room room = currentRoom();

        if (room == null || room.getId() != roomId || !room.getRaidProtection().canManage(this.client.getHabbo())) {
            return;
        }

        this.client.sendResponse(new RaidProtectionSettingsComposer(
                room.getRaidProtection().settings(),
                room.getRaidProtection().incidentActive(),
                room.getRaidProtection().lastRaidAtSeconds()));
    }

    @Override
    public int getRatelimit() {
        return 500;
    }
}
