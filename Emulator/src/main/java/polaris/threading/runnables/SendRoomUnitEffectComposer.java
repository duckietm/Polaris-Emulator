package polaris.threading.runnables;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.messages.outgoing.rooms.users.RoomUserEffectComposer;

public class SendRoomUnitEffectComposer implements Runnable {
    private final Room room;
    private final RoomUnit roomUnit;

    public SendRoomUnitEffectComposer(Room room, RoomUnit roomUnit) {
        this.room = room;
        this.roomUnit = roomUnit;
    }

    @Override
    public void run() {
        if (this.room != null && this.roomUnit != null) {
            this.room.sendComposer(new RoomUserEffectComposer(roomUnit).compose());
        }
    }
}
