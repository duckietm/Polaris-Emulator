package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.slf4j.LoggerFactory;

public class BanzaiRandomTeleport implements Runnable {
    private final HabboItem item;
    private final HabboItem toItem;
    private final RoomUnit habbo;
    private final Room room;

    public BanzaiRandomTeleport(HabboItem item, HabboItem toItem, RoomUnit habbo, Room room) {
        this.item = item;
        this.toItem = toItem;
        this.habbo = habbo;
        this.room = room;
    }

    @Override
    public void run() {
        HabboItem topItemNow = this.room.getTopItemAt(this.habbo.getX(), this.habbo.getY());
        RoomTile lastLocation = this.habbo.getCurrentLocation();
        RoomTile newLocation = this.room.getLayout().getTile(toItem.getX(), toItem.getY());

        if (topItemNow != null) {
            try {
                topItemNow.onWalkOff(this.habbo, this.room, new Object[] {lastLocation, newLocation, this});
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
                            this.habbo.setCanWalk(true);
                            HabboItem topItemNext = this.room.getTopItemAt(this.habbo.getX(), this.habbo.getY());

                            if (topItemNext != null) {
                                try {
                                    topItemNext.onWalkOn(
                                            this.habbo, this.room, new Object[] {lastLocation, newLocation, this});
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
                            this.habbo.setRotation(RoomUserRotation.fromValue(
                                    Emulator.getRandom().nextInt(8)));
                            // A ridden horse goes along with its rider, who sits one higher on it.
                            RoomUnit horse = ridingHorse(this.room, this.habbo);
                            double riderZ = newLocation.getStackHeight() + (horse != null ? 1.0D : 0.0D);
                            this.room.teleportRoomUnitToLocation(this.habbo, newLocation.x, newLocation.y, riderZ);

                            if (horse != null) {
                                horse.setRotation(this.habbo.getBodyRotation());
                                this.room.teleportRoomUnitToLocation(
                                        horse, newLocation.x, newLocation.y, newLocation.getStackHeight());
                            }
                        },
                        250);
    }

    /** The horse the unit rides, or null. */
    static RoomUnit ridingHorse(Room room, RoomUnit unit) {
        Habbo rider = room.getHabbo(unit);
        if (rider == null
                || rider.getHabboInfo() == null
                || rider.getHabboInfo().getRiding() == null) {
            return null;
        }
        RoomUnit horse = rider.getHabboInfo().getRiding().getRoomUnit();
        return horse != unit ? horse : null;
    }
}
