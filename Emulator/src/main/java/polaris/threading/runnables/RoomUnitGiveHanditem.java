package polaris.threading.runnables;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.messages.outgoing.rooms.users.RoomUserHandItemComposer;

public class RoomUnitGiveHanditem implements Runnable {
    private final RoomUnit roomUnit;
    private final Room room;
    private final int itemId;

    public RoomUnitGiveHanditem(RoomUnit roomUnit, Room room, int itemId) {
        this.roomUnit = roomUnit;
        this.room = room;
        this.itemId = itemId;
    }

    @Override
    public void run() {
        if (this.room != null && this.roomUnit.isInRoom()) {
            this.roomUnit.setHandItem(this.itemId);
            this.room.sendComposer(new RoomUserHandItemComposer(this.roomUnit).compose());
        }
    }
}
