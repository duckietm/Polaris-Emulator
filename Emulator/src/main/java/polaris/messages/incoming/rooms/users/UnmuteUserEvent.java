package polaris.messages.incoming.rooms.users;

import polaris.Emulator;
import polaris.hotel.commands.CommandTargetGuard;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

/**
 * Official {@code RoomSession.unmuteUser(userId)} -> header 3302 {@code (userId, roomId)}: the
 * {@code ambassador_unmute} row of the avatar menu (mode 7, {@code AvatarMenuView.as:180}).
 */
public class UnmuteUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        int roomId = this.packet.readInt();

        User moderator = this.client.getUser();

        if (moderator == null) {
            return;
        }

        boolean allowed = moderator.hasPermission(Permission.ACC_AMBASSADOR)
                || moderator.hasPermission(Permission.ACC_SUPPORTTOOL);

        if (!allowed) {
            return;
        }

        User target = Emulator.getGameEnvironment().getUserManager().getUser(userId);

        // Not yourself, and not someone of your rank or above (their mute may come from staff).
        if (!CommandTargetGuard.canTarget(moderator, target)) {
            return;
        }

        Room room = target.getUserInfo().getCurrentRoom();

        if (roomId > 0 && (room == null || room.getId() != roomId)) {
            return;
        }

        target.getUserStats().unMute();

        // The room mute an ambassador gave lifts with it.
        if (room != null && room.isMuted(target)) {
            room.unmuteUser(target);
        }
    }
}
