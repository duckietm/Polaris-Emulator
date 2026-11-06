package polaris.threading.runnables.hopper;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;

public class HopperActionOne implements Runnable {
    private final UserItem teleportOne;
    private final Room room;
    private final GameClient client;

    public HopperActionOne(UserItem teleportOne, Room room, GameClient client) {
        this.teleportOne = teleportOne;
        this.room = room;
        this.client = client;
    }

    @Override
    public void run() {
        // this.client.getUser().getRoomUnit().setGoalLocation(this.teleportOne.getX(), this.teleportOne.getY());
        this.client
                .getUser()
                .getRoomUnit()
                .setRotation(RoomUserRotation.values()[(this.teleportOne.getRotation() + 4) % 8]);
        this.client
                .getUser()
                .getRoomUnit()
                .setStatus(
                        RoomUnitStatus.MOVE,
                        this.teleportOne.getX() + "," + this.teleportOne.getY() + "," + this.teleportOne.getZ());
        this.room.scheduledComposers.add(
                new RoomUserStatusComposer(this.client.getUser().getRoomUnit()).compose());
        this.client
                .getUser()
                .getRoomUnit()
                .setLocation(this.room.getLayout().getTile(this.teleportOne.getX(), this.teleportOne.getY()));
        this.client.getUser().getRoomUnit().setZ(this.teleportOne.getZ());
        this.client.getUser().getRoomUnit().setPreviousLocationZ(this.teleportOne.getZ());

        Emulator.getThreading()
                .run(
                        () -> {
                            HopperActionOne.this.client.getUser().getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
                            HopperActionOne.this.room.sendComposer(new RoomUserStatusComposer(HopperActionOne.this
                                            .client
                                            .getUser()
                                            .getRoomUnit())
                                    .compose());
                        },
                        750);

        Emulator.getThreading().run(new HopperActionTwo(this.teleportOne, this.room, this.client), 1250);
    }
}
