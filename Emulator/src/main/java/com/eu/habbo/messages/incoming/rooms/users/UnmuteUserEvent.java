package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Official {@code RoomSession.unmuteUser(userId)} -> header 3302 {@code (userId, roomId)}: the
 * {@code ambassador_unmute} row of the avatar menu (mode 7, {@code AvatarMenuView.as:180}).
 */
public class UnmuteUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        int roomId = this.packet.readInt();

        Habbo moderator = this.client.getHabbo();

        if (moderator == null) {
            return;
        }

        boolean allowed = moderator.hasPermission(Permission.ACC_AMBASSADOR)
                || moderator.hasPermission(Permission.ACC_SUPPORTTOOL);

        if (!allowed) {
            return;
        }

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (target == null) {
            return;
        }

        Room room = target.getHabboInfo().getCurrentRoom();

        if (roomId > 0 && (room == null || room.getId() != roomId)) {
            return;
        }

        target.getHabboStats().unMute();
    }
}
