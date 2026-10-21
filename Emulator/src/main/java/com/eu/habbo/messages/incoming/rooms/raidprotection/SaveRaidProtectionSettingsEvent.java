package com.eu.habbo.messages.incoming.rooms.raidprotection;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionManager;
import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionSettings;
import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionSnapshot;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.raidprotection.RaidProtectionSaveResultComposer;

/**
 * AIR 2170 (UnkMessageComposer_9args_0904d6, RaidProtectionSettingsController.sendSave): room id,
 * enabled, sensitivity, action, ban duration, guard enabled, guard duration, guard sensitivity and
 * whether the owner confirmed switching protection on. Answered with 958 and the settings in force.
 */
public class SaveRaidProtectionSettingsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();
        boolean enabled = this.packet.readBoolean();
        int sensitivity = this.packet.readInt();
        int actionType = this.packet.readInt();
        int banDuration = this.packet.readInt();
        boolean guardEnabled = this.packet.readBoolean();
        int guardDuration = this.packet.readInt();
        int guardSensitivity = this.packet.readInt();
        boolean confirmed = this.packet.readBoolean();

        RaidProtectionSettings requested = new RaidProtectionSettings(
                roomId, enabled, sensitivity, actionType, banDuration, guardEnabled, guardDuration, guardSensitivity);

        Room room = this.currentRoom();
        if (room == null || room.getId() != roomId) {
            this.client.sendResponse(new RaidProtectionSaveResultComposer(
                    RaidProtectionManager.isFeatureEnabled()
                            ? RaidProtectionManager.RESULT_ROOM_UNAVAILABLE
                            : RaidProtectionManager.RESULT_FEATURE_DISABLED,
                    new RaidProtectionSnapshot(RaidProtectionSettings.defaults(roomId), false, 0)));
            return;
        }

        RaidProtectionManager manager = RaidProtectionManager.get();
        int result = manager.save(room, this.client.getHabbo(), requested, confirmed);
        RaidProtectionSnapshot snapshot = RaidProtectionManager.canManage(room, this.client.getHabbo())
                ? manager.snapshot(room)
                : new RaidProtectionSnapshot(RaidProtectionSettings.defaults(roomId), false, 0);
        this.client.sendResponse(new RaidProtectionSaveResultComposer(result, snapshot));
    }
}
