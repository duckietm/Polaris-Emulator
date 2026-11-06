package polaris.hotel.items.interactions.interfaces;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;

public interface ConditionalGate {
    public void onRejected(RoomUnit roomUnit, Room room, Object[] objects);
}
