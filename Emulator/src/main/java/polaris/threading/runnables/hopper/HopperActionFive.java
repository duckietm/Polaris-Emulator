package polaris.threading.runnables.hopper;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;
import polaris.threading.runnables.UserItemNewState;

class HopperActionFive implements Runnable {
    private final UserItem currentTeleport;
    private final Room room;
    private final GameClient client;

    public HopperActionFive(UserItem currentTeleport, Room room, GameClient client) {
        this.currentTeleport = currentTeleport;
        this.client = client;
        this.room = room;
    }

    @Override
    public void run() {
        this.client.getUser().getRoomUnit().isTeleporting = false;
        RoomTile tile = this.room
                .getLayout()
                .getTileInFront(
                        this.room.getLayout().getTile(this.currentTeleport.getX(), this.currentTeleport.getY()),
                        this.currentTeleport.getRotation());
        if (tile != null) {
            this.client.getUser().getRoomUnit().setGoalLocation(tile);
        }

        Emulator.getThreading().run(new UserItemNewState(this.currentTeleport, this.room, "0"), 1000);
    }
}
