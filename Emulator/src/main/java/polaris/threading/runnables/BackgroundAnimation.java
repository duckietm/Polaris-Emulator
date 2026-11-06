package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;

public class BackgroundAnimation implements Runnable {
    private final UserItem toner;
    private final Room room;
    private int length = 1000;
    private int state = 0;

    public BackgroundAnimation(UserItem toner, Room room) {
        this.toner = toner;
        this.room = room;
    }

    @Override
    public void run() {
        if (this.room.isLoaded() && !this.room.isPreLoaded()) {
            this.toner.setExtradata("1:" + this.state + ":126:126");
            this.state = (this.state + 1) % 256;
            this.room.updateItem(this.toner);

            if (this.toner.getRoomId() > 0 && this.length > 0) {
                Emulator.getThreading().run(this, 500);
                this.length--;
            }
        }
    }
}
