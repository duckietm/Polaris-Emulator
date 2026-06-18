package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.util.PacketGuard;

public class RoomUserRemoveRightsEvent extends MessageHandler {
    private static final int MAX_RIGHTS_REMOVALS = 100;
    private static final int BYTES_PER_USER_ID = 4;

    @Override
    public void handle() throws Exception {
        int amount = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        if (room.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            if (!PacketGuard.isCountInRange(amount, 1, MAX_RIGHTS_REMOVALS)
                    || !PacketGuard.hasFixedWidthEntries(this.packet, amount, BYTES_PER_USER_ID)) {
                return;
            }

            for (int i = 0; i < amount; i++) {
                int userId = this.packet.readInt();

                if (!RoomUserInputGuard.isPositiveId(userId))
                    continue;

                room.removeRights(userId);
            }
        }
    }
}
