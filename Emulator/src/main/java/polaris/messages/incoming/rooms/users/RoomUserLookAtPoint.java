package polaris.messages.incoming.rooms.users;

import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserClicksUser;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class RoomUserLookAtPoint extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        User user = this.client.getUser();

        if (user.getRoomUnit().getCacheable().get("control") != null) {
            user = (User) this.client.getUser().getRoomUnit().getCacheable().get("control");

            if (user.getUserInfo().getCurrentRoom()
                    != this.client.getUser().getUserInfo().getCurrentRoom()) {
                user.getRoomUnit().getCacheable().remove("controller");
                this.client.getUser().getRoomUnit().getCacheable().remove("control");
                user = this.client.getUser();
            }
        }

        RoomUnit roomUnit = user.getRoomUnit();

        if (!roomUnit.canWalk()) return;

        if (roomUnit.isWalking() || roomUnit.hasStatus(RoomUnitStatus.MOVE)) return;

        if (roomUnit.cmdLay || roomUnit.hasStatus(RoomUnitStatus.LAY)) return;

        if (roomUnit.isIdle()) return;

        if (WiredTriggerUserClicksUser.consumeIgnoreLook(roomUnit)) return;

        int x = this.packet.readInt();
        int y = this.packet.readInt();

        if (x == roomUnit.getX() && y == roomUnit.getY()) return;

        RoomTile tile = user.getUserInfo().getCurrentRoom().getLayout().getTile((short) x, (short) y);

        if (tile != null) {
            roomUnit.lookAtPoint(tile);
            roomUnit.statusUpdate(true);
            // room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        }
    }
}
