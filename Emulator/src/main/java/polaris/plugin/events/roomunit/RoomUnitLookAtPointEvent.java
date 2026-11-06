package polaris.plugin.events.roomunit;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;

public class RoomUnitLookAtPointEvent extends RoomUnitEvent {

    public final RoomTile location;

    public RoomUnitLookAtPointEvent(Room room, RoomUnit roomUnit, RoomTile location) {
        super(room, roomUnit);

        this.location = location;
    }
}
