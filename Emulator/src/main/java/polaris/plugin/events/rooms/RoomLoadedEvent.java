package polaris.plugin.events.rooms;

import polaris.hotel.rooms.Room;

public class RoomLoadedEvent extends RoomEvent {

    public RoomLoadedEvent(Room room) {
        super(room);
    }
}
