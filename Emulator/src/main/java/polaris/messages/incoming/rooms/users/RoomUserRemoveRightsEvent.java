package polaris.messages.incoming.rooms.users;

import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.util.PacketGuard;

public class RoomUserRemoveRightsEvent extends MessageHandler {
    private static final int MAX_RIGHTS_REMOVALS = 100;
    private static final int BYTES_PER_USER_ID = 4;

    @Override
    public void handle() throws Exception {
        int amount = this.packet.readInt();

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        if (room.getOwnerId() == this.client.getUser().getUserInfo().getId()
                || this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            if (!PacketGuard.isCountInRange(amount, 1, MAX_RIGHTS_REMOVALS)
                    || !PacketGuard.hasFixedWidthEntries(this.packet, amount, BYTES_PER_USER_ID)) {
                return;
            }

            for (int i = 0; i < amount; i++) {
                int userId = this.packet.readInt();

                if (!RoomUserInputGuard.isPositiveId(userId)) continue;

                room.removeRights(userId);
            }
        }
    }
}
