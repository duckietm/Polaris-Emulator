package polaris.plugin.events.roomunit;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.plugin.Event;

public abstract class RoomUnitEvent extends Event {

    public final Room room;

    public final RoomUnit roomUnit;

    public RoomUnitEvent(Room room, RoomUnit roomUnit) {
        this.room = room;
        this.roomUnit = roomUnit;
    }
}
