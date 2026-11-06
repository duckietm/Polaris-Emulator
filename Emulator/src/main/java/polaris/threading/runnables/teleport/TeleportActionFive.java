package polaris.threading.runnables.teleport;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.interactions.InteractionTeleportTile;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredFreezeUtil;
import polaris.threading.runnables.RoomUnitWalkToLocation;
import polaris.threading.runnables.UserItemNewState;

class TeleportActionFive implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeleportActionFive.class);

    private final UserItem currentTeleport;
    private final Room room;
    private final User user;

    public TeleportActionFive(UserItem currentTeleport, Room room, GameClient client) {
        this(currentTeleport, room, client == null ? null : client.getUser());
    }

    public TeleportActionFive(UserItem currentTeleport, Room room, User user) {
        this.currentTeleport = currentTeleport;
        this.user = user;
        this.room = room;
    }

    @Override
    public void run() {
        TeleportChain.run(this.user, this.currentTeleport, this.room, this::step);
    }

    private void step() {
        RoomUnit unit = this.user.getRoomUnit();

        unit.isLeavingTeleporter = false;
        unit.isTeleporting = false;
        unit.setCanWalk(true);

        if (this.user.getUserInfo().getCurrentRoom() != this.room) {
            this.user.getUserInfo().setLoadingRoom(0);
            return;
        }

        // if (!(this.currentTeleport instanceof InteractionTeleportTile))

        if (this.room.getLayout() == null || this.currentTeleport == null) return;

        RoomTile currentLocation =
                this.room.getLayout().getTile(this.currentTeleport.getX(), this.currentTeleport.getY());
        RoomTile tile = this.room.getLayout().getTileInFront(currentLocation, this.currentTeleport.getRotation());

        if (tile != null) {
            List<Runnable> onSuccess = new ArrayList<Runnable>();
            onSuccess.add(() -> {
                unit.setCanLeaveRoomByDoor(true);
                WiredFreezeUtil.restoreWalkState(unit);

                Emulator.getThreading()
                        .run(
                                () -> {
                                    unit.isLeavingTeleporter = false;
                                },
                                300);
            });

            unit.setCanLeaveRoomByDoor(false);
            unit.setGoalLocation(tile);
            unit.statusUpdate(true);
            unit.isLeavingTeleporter = true;
            Emulator.getThreading().run(new RoomUnitWalkToLocation(unit, tile, room, onSuccess, onSuccess));
        } else {
            WiredFreezeUtil.restoreWalkState(unit);
        }

        this.currentTeleport.setExtradata("1");
        this.room.updateItem(this.currentTeleport);

        Emulator.getThreading().run(new UserItemNewState(this.currentTeleport, this.room, "0"), 1000);

        UserItem teleportTile = this.room.getTopItemAt(unit.getX(), unit.getY());

        if (teleportTile != null
                && teleportTile instanceof InteractionTeleportTile
                && teleportTile != this.currentTeleport) {
            try {
                teleportTile.onWalkOn(unit, this.room, new Object[] {});
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }
}
