package com.eu.habbo.messages.incoming.rooms.raidprotection;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionSettings;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.raidprotection.RaidProtectionSaveResultComposer;

/**
 * Official AIR 15 raid protection save.
 *
 * <p>The official header for this packet is 2687, which this emulator already uses for
 * {@code SetStackHelperAdjacentHeightEvent}, so it lives in the custom features range instead.
 *
 * <p>The trailing {@code confirmed} flag is the client's own two-step: turning the protection on
 * opens a confirmation dialog and the same packet is sent again with the flag set. The server does
 * not need it to decide anything, and reads it only to consume the field.
 */
public class RaidProtectionSettingsSaveEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();
        boolean enabled = this.packet.readBoolean();
        int detectionSensitivity = this.packet.readInt();
        int actionType = this.packet.readInt();
        int banDurationSeconds = this.packet.readInt();
        boolean guardEnabled = this.packet.readBoolean();
        int guardDurationSeconds = this.packet.readInt();
        int guardSensitivity = this.packet.readInt();
        this.packet.readBoolean();

        Room room = currentRoom();

        if (room == null || room.getId() != roomId) {
            return;
        }

        RaidProtectionSettings requested = new RaidProtectionSettings(
                roomId,
                enabled,
                detectionSensitivity,
                actionType,
                banDurationSeconds,
                guardEnabled,
                guardDurationSeconds,
                guardSensitivity);
        int result = room.getRaidProtection().save(this.client.getHabbo(), requested);

        this.client.sendResponse(new RaidProtectionSaveResultComposer(
                result,
                room.getRaidProtection().settings(),
                room.getRaidProtection().incidentActive(),
                room.getRaidProtection().lastRaidAtSeconds()));
    }

    @Override
    public int getRatelimit() {
        return 500;
    }
}
