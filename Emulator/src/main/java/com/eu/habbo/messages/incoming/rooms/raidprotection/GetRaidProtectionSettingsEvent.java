package com.eu.habbo.messages.incoming.rooms.raidprotection;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.raidprotection.RaidProtectionCapabilityComposer;
import com.eu.habbo.messages.outgoing.rooms.raidprotection.RaidProtectionSettingsComposer;

/**
 * AIR 2394 (UnkMessageComposer_1args_94bac7): the owner opens the raid protection window of the
 * room they stand in (link navigator/raidprotection/&lt;roomId&gt;). Answered with 3877; someone
 * who may not manage it gets 2671 with false, which hides the button again.
 */
public class GetRaidProtectionSettingsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();
        if (!RaidProtectionManager.isFeatureEnabled()) return;

        Room room = this.currentRoom();
        if (room == null || room.getId() != roomId) return;

        RaidProtectionManager manager = RaidProtectionManager.get();
        if (!RaidProtectionManager.canManage(room, this.client.getHabbo())) {
            this.client.sendResponse(new RaidProtectionCapabilityComposer(roomId, false));
            return;
        }

        this.client.sendResponse(new RaidProtectionSettingsComposer(manager.snapshot(room)));
    }
}
