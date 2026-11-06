package polaris.plugin.events.rooms;

import polaris.hotel.rooms.Room;

public class RoomUnloadedEvent extends RoomEvent {

    public RoomUnloadedEvent(Room room) {
        super(room);
    }
}
