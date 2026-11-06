package polaris.hotel.items;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;

public interface IEventTriggers {
    void onClick(GameClient client, Room room, Object[] objects) throws Exception;

    void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception;

    void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception;
}
