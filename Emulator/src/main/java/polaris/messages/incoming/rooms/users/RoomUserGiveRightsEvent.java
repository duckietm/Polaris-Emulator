package polaris.messages.incoming.rooms.users;

import polaris.Emulator;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserRightsGivenEvent;

public class RoomUserGiveRightsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        if (!RoomUserInputGuard.isPositiveId(userId)) {
            return;
        }

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        if (room.getOwnerId() == this.client.getUser().getUserInfo().getId()
                || this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            User target = room.getUser(userId);

            if (target != null) {
                if (!Emulator.getPluginManager()
                        .fireEvent(new UserRightsGivenEvent(this.client.getUser(), target))
                        .isCancelled()) {
                    room.giveRights(target);
                }
            } else {
                MessengerBuddy buddy = this.client.getUser().getMessenger().getFriend(userId);

                if (buddy != null) {
                    room.giveRights(userId);
                }
            }
        }
    }
}
