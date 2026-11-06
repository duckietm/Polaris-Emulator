package polaris.plugin.events.rooms;

import polaris.hotel.rooms.Room;

public class RoomUncachedEvent extends RoomEvent {

    public RoomUncachedEvent(Room room) {
        super(room);
    }
}
