package polaris.messages.incoming.rooms;

import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomRightLevels;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomRemoveRightsListComposer;
import polaris.messages.outgoing.rooms.RoomRightsComposer;

public class RoomRemoveAllRightsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        final Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null || room.getId() != this.packet.readInt()) return;

        if (room.getOwnerId() == this.client.getUser().getUserInfo().getId()
                || this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            for (int value : room.getRights().toIntArray()) {
                User user = room.getUser(value);

                if (user != null) {
                    room.sendComposer(new RoomRemoveRightsListComposer(room, value).compose());
                    user.getRoomUnit().removeStatus(RoomUnitStatus.FLAT_CONTROL);
                    user.getClient().sendResponse(new RoomRightsComposer(RoomRightLevels.NONE));
                }
            }

            room.removeAllRights();
        }
    }
}
