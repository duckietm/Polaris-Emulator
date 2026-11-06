package polaris.threading.runnables.teleport;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.interactions.InteractionTeleportTile;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredFreezeUtil;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;

public class TeleportActionOne implements Runnable {
    private final UserItem currentTeleport;
    private final Room room;
    private final User user;

    public TeleportActionOne(UserItem currentTeleport, Room room, GameClient client) {
        this(currentTeleport, room, client == null ? null : client.getUser());
    }

    public TeleportActionOne(UserItem currentTeleport, Room room, User user) {
        this.currentTeleport = currentTeleport;
        this.user = user;
        this.room = room;
    }

    @Override
    public void run() {
        TeleportChain.run(this.user, this.currentTeleport, this.room, this::step);
    }

    private void step() {
        if (this.user.getUserInfo().getCurrentRoom() != this.room) {
            this.user.getUserInfo().setLoadingRoom(0);
            this.user.getRoomUnit().isTeleporting = false;
            WiredFreezeUtil.restoreWalkState(this.user.getRoomUnit());
            return;
        }

        int delay = 500;

        if (this.currentTeleport instanceof InteractionTeleportTile) {
            delay = 0;
        }

        if (this.user.getRoomUnit().getCurrentLocation()
                != this.room.getLayout().getTile(this.currentTeleport.getX(), this.currentTeleport.getY())) {
            this.user
                    .getRoomUnit()
                    .setLocation(
                            this.room.getLayout().getTile(this.currentTeleport.getX(), this.currentTeleport.getY()));
            this.user
                    .getRoomUnit()
                    .setRotation(RoomUserRotation.values()[(this.currentTeleport.getRotation() + 4) % 8]);
            this.user
                    .getRoomUnit()
                    .setStatus(
                            RoomUnitStatus.MOVE,
                            this.currentTeleport.getX() + "," + this.currentTeleport.getY() + ","
                                    + this.currentTeleport.getZ());
            this.room.scheduledComposers.add(new RoomUserStatusComposer(this.user.getRoomUnit()).compose());
            this.user
                    .getRoomUnit()
                    .setLocation(
                            this.room.getLayout().getTile(this.currentTeleport.getX(), this.currentTeleport.getY()));
        }

        Emulator.getThreading().run(new TeleportActionTwo(this.currentTeleport, this.room, this.user), delay);
    }
}
