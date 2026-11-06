package polaris.plugin.events.rooms;

import polaris.hotel.rooms.Room;
import polaris.plugin.Event;

public abstract class RoomEvent extends Event {

    public final Room room;

    public RoomEvent(Room room) {
        this.room = room;
    }
}
