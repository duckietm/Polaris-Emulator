package polaris.threading.runnables;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;

public class CloseGate implements Runnable {
    private final UserItem gate;
    private final Room room;

    public CloseGate(UserItem gate, Room room) {
        this.gate = gate;
        this.room = room;
    }

    @Override
    public void run() {
        if (this.gate.getRoomId() == this.room.getId()) {
            if (this.room.isLoaded()) {
                if (this.room.getUsersAt(this.gate.getX(), this.gate.getY()).isEmpty()) {
                    this.gate.setExtradata("0");
                    this.room.updateItem(this.gate);
                    this.gate.needsUpdate(true);
                }
            }
        }
    }
}
