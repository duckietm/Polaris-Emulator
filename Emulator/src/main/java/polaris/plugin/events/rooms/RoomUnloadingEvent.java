package polaris.plugin.events.rooms;

import polaris.hotel.rooms.Room;

public class RoomUnloadingEvent extends RoomEvent {

    public RoomUnloadingEvent(Room room) {
        super(room);
    }
}
