package polaris.threading.runnables;

import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredFreezeUtil;

public class RoomUnitTeleport implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUnitTeleport.class);

    private RoomUnit roomUnit;
    private Room room;
    private int x;
    private int y;
    private double z;
    private boolean walkOnAtLanding = true;

    public RoomUnitTeleport(RoomUnit roomUnit, Room room, int x, int y, double z, int newEffect) {
        this.roomUnit = roomUnit;
        this.room = room;
        this.x = x;
        this.y = y;
        this.z = z;
        // newEffect parameter is unused
    }

    /** Leaves Walks On out of the landing, for a teleport that raised it already (AIR's normal Teleport To). */
    public RoomUnitTeleport withoutWalkOn() {
        this.walkOnAtLanding = false;
        return this;
    }

    @Override
    public void run() {
        if (roomUnit == null || roomUnit.getRoom() == null || room.getLayout() == null) return;

        if (roomUnit.isLeavingTeleporter) {
            roomUnit.isWiredTeleporting = false;
            return;
        }

        WiredFreezeUtil.onWiredTeleport(this.room, this.roomUnit);

        RoomTile lastLocation = this.roomUnit.getCurrentLocation();
        RoomTile newLocation = this.room.getLayout().getTile((short) this.x, (short) this.y);

        UserItem topItem =
                this.room.getTopItemAt(this.roomUnit.getCurrentLocation().x, this.roomUnit.getCurrentLocation().y);
        if (topItem != null) {
            try {
                topItem.onWalkOff(this.roomUnit, this.room, new Object[] {this});
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
        this.roomUnit.setPath(new LinkedList<>());
        this.roomUnit.setCurrentLocation(newLocation);
        this.roomUnit.setPreviousLocation(newLocation);
        this.roomUnit.setZ(this.z);
        this.roomUnit.setPreviousLocationZ(this.z);
        this.roomUnit.removeStatus(RoomUnitStatus.MOVE);
        // ServerMessage teleportMessage = new RoomUnitOnRollerComposer(this.roomUnit, newLocation,
        // this.room).compose();
        this.roomUnit.setLocation(newLocation);
        // this.room.sendComposer(teleportMessage);
        this.roomUnit.statusUpdate(true);
        roomUnit.isWiredTeleporting = false;
        WiredFreezeUtil.restoreWalkState(this.roomUnit);

        this.room.updateUsersAt(newLocation.x, newLocation.y);
        this.room.updateBotsAt(newLocation.x, newLocation.y);

        topItem = this.walkOnAtLanding ? room.getTopItemAt(x, y) : null;
        if (topItem != null
                && roomUnit.getCurrentLocation().equals(room.getLayout().getTile((short) x, (short) y))) {
            try {
                topItem.onWalkOn(roomUnit, room, new Object[] {lastLocation, newLocation, this});
            } catch (Exception e) {
                LOGGER.error("Walk-on of item ID ({}) failed after a teleport", topItem.getId(), e);
            }
        }
    }
}
