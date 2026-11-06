package polaris.threading.runnables;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredManager;

public class WiredCollissionRunnable implements Runnable {
    public final RoomUnit roomUnit;
    public final Room room;
    public final UserItem collidingFurni;

    public WiredCollissionRunnable(RoomUnit roomUnit, Room room) {
        this(roomUnit, room, null);
    }

    public WiredCollissionRunnable(RoomUnit roomUnit, Room room, UserItem collidingFurni) {
        this.roomUnit = roomUnit;
        this.room = room;
        this.collidingFurni = collidingFurni;
    }

    @Override
    public void run() {
        if (this.roomUnit == null || this.room == null || !this.room.isLoaded()) return;
        try {
            WiredManager.triggerBotCollision(room, roomUnit, collidingFurni);
        } catch (Exception e) {
            // Prevent task from crashing the thread pool
        }
    }
}
