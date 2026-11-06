package polaris.threading.runnables.hopper;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;

class HopperActionFour implements Runnable {
    private final UserItem currentTeleport;
    private final Room room;
    private final GameClient client;

    public HopperActionFour(UserItem currentTeleport, Room room, GameClient client) {
        this.currentTeleport = currentTeleport;
        this.client = client;
        this.room = room;
    }

    @Override
    public void run() {
        this.currentTeleport.setExtradata("1");
        this.room.updateItem(this.currentTeleport);

        Emulator.getThreading().run(new HopperActionFive(this.currentTeleport, this.room, this.client), 500);
    }
}
