package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.rooms.Room;

public class LoadCustomHeightMap implements Runnable {
    private final Room room;

    public LoadCustomHeightMap(Room room) {
        this.room = room;
    }

    @Override
    public void run() {
        this.room.setLayout(Emulator.getGameEnvironment().getRoomManager().loadCustomLayout(this.room));
    }
}
