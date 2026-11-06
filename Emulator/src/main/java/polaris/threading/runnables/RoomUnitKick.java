package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public class RoomUnitKick implements Runnable {
    private final User user;
    private final Room room;
    private final boolean removeEffect;

    public RoomUnitKick(User user, Room room, boolean removeEffect) {
        this.user = user;
        this.room = room;
        this.removeEffect = removeEffect;
    }

    @Override
    public void run() {
        if (this.removeEffect) {
            this.user.getRoomUnit().setEffectId(0, 0);
        }

        Emulator.getGameEnvironment().getRoomManager().leaveRoom(this.user, this.room);
    }
}
