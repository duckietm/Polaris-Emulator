package polaris.threading.runnables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class RoomUnitVendingMachineAction implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUnitVendingMachineAction.class);

    private final User user;
    private final UserItem userItem;
    private final Room room;

    public RoomUnitVendingMachineAction(User user, UserItem userItem, Room room) {
        this.user = user;
        this.userItem = userItem;
        this.room = room;
    }

    @Override
    public void run() {
        if (this.user.getUserInfo().getCurrentRoom() == this.room) {
            if (this.userItem.getRoomId() == this.room.getId()) {
                RoomTile tile = UserItem.getSquareInFront(this.room.getLayout(), this.userItem);
                if (tile != null) {
                    if (this.user.getRoomUnit().getGoal().equals(tile)) {
                        if (this.user.getRoomUnit().getCurrentLocation().equals(tile)) {
                            try {
                                this.userItem.onClick(this.user.getClient(), this.room, new Object[] {0});
                            } catch (Exception e) {
                                LOGGER.error("Caught exception", e);
                            }
                        } else {
                            if (this.room.getLayout().getTile(tile.x, tile.y).isWalkable()) {
                                this.user.getRoomUnit().setGoalLocation(tile);
                                Emulator.getThreading()
                                        .run(
                                                this,
                                                this.user
                                                                .getRoomUnit()
                                                                .getPath()
                                                                .size()
                                                        + 2 * 510);
                            }
                        }
                    }
                }
            }
        }
    }
}
