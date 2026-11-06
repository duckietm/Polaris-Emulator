package polaris.threading.runnables;

import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class BanzaiRandomTeleport implements Runnable {
    private final UserItem item;
    private final UserItem toItem;
    private final RoomUnit user;
    private final Room room;

    public BanzaiRandomTeleport(UserItem item, UserItem toItem, RoomUnit user, Room room) {
        this.item = item;
        this.toItem = toItem;
        this.user = user;
        this.room = room;
    }

    @Override
    public void run() {
        UserItem topItemNow = this.room.getTopItemAt(this.user.getX(), this.user.getY());
        RoomTile lastLocation = this.user.getCurrentLocation();
        RoomTile newLocation = this.room.getLayout().getTile(toItem.getX(), toItem.getY());

        if (topItemNow != null) {
            try {
                topItemNow.onWalkOff(this.user, this.room, new Object[] {lastLocation, newLocation, this});
            } catch (Exception e) {
                LoggerFactory.getLogger(BanzaiRandomTeleport.class).error("BanzaiRandomTeleport exception", e);
            }
        }

        Emulator.getThreading()
                .run(
                        () -> {
                            if (this.item.getExtradata().equals("1")) {
                                this.item.setExtradata("0");
                                this.room.updateItemState(this.item);
                            }
                        },
                        500);

        if (!this.toItem.getExtradata().equals("1")) {
            this.toItem.setExtradata("1");
            this.room.updateItemState(this.toItem);
        }

        Emulator.getThreading()
                .run(
                        () -> {
                            this.user.setCanWalk(true);
                            UserItem topItemNext = this.room.getTopItemAt(this.user.getX(), this.user.getY());

                            if (topItemNext != null) {
                                try {
                                    topItemNext.onWalkOn(
                                            this.user, this.room, new Object[] {lastLocation, newLocation, this});
                                } catch (Exception e) {
                                    LoggerFactory.getLogger(BanzaiRandomTeleport.class)
                                            .error("BanzaiRandomTeleport exception", e);
                                }
                            }

                            if (this.toItem.getExtradata().equals("1")) {
                                this.toItem.setExtradata("0");
                                this.room.updateItemState(this.toItem);
                            }
                        },
                        750);

        Emulator.getThreading()
                .run(
                        () -> {
                            this.user.setRotation(RoomUserRotation.fromValue(
                                    Emulator.getRandom().nextInt(8)));
                            // A ridden horse goes along with its rider, who sits one higher on it.
                            RoomUnit horse = ridingHorse(this.room, this.user);
                            double riderZ = newLocation.getStackHeight() + (horse != null ? 1.0D : 0.0D);
                            this.room.teleportRoomUnitToLocation(this.user, newLocation.x, newLocation.y, riderZ);

                            if (horse != null) {
                                horse.setRotation(this.user.getBodyRotation());
                                this.room.teleportRoomUnitToLocation(
                                        horse, newLocation.x, newLocation.y, newLocation.getStackHeight());
                            }
                        },
                        250);
    }

    /** The horse the unit rides, or null. */
    static RoomUnit ridingHorse(Room room, RoomUnit unit) {
        User rider = room.getUser(unit);
        if (rider == null || rider.getUserInfo() == null || rider.getUserInfo().getRiding() == null) {
            return null;
        }
        RoomUnit horse = rider.getUserInfo().getRiding().getRoomUnit();
        return horse != unit ? horse : null;
    }
}
