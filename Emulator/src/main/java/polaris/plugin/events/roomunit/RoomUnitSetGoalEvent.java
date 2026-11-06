package polaris.plugin.events.roomunit;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;

public class RoomUnitSetGoalEvent extends RoomUnitEvent {

    public final RoomTile goal;

    public RoomUnitSetGoalEvent(Room room, RoomUnit roomUnit, RoomTile goal) {
        super(room, roomUnit);

        this.goal = goal;
    }

    public void setGoal(RoomTile t) {
        super.roomUnit.setGoalLocation(t);
    }
}
