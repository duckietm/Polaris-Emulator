package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserRightsGivenEvent;

public class RoomUserGiveRightsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        if (!RoomUserInputGuard.isPositiveId(userId)) {
            return;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        if (room.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            Habbo target = room.getHabbo(userId);

            if (target != null) {
                if (!Emulator.getPluginManager().fireEvent(new UserRightsGivenEvent(this.client.getHabbo(), target)).isCancelled()) {
                    room.giveRights(target);
                }
            } else {
                MessengerBuddy buddy = this.client.getHabbo().getMessenger().getFriend(userId);

                if (buddy != null) {
                    room.giveRights(userId);
                }
            }
        }
    }
}
